package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.cqrs4j.springboot.pm.core.CommandOutboxService.Entry;
import org.fuin.objects4j.common.Contract;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Drains the command outbox: on every (cron scheduled) run it reads a batch of queued commands and
 * POSTs each one to the configured command endpoint. A successfully delivered command is deleted from
 * the outbox; a failed one has its retry counter incremented and is moved to the dead-letter table
 * once it exhausts {@link CommandQueueConfig#getMaxRetries()} attempts.
 * <p>
 * The HTTP call happens outside any database transaction; each resulting outbox mutation runs in its
 * own {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} transaction so that one failing command
 * does not roll back the successful delivery of its siblings. A {@link ReentrantLock} guards against
 * overlapping runs.
 */
@ThreadSafe
@Component
public class CommandQueueExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(CommandQueueExecutor.class);

    private final CommandOutboxService outboxService;

    private final CommandRestClient commandRestClient;

    private final CommandQueueConfig config;

    private final TransactionTemplate requiresNewTransaction;

    private final Lock lock = new ReentrantLock();

    /**
     * Guards the delivery of a queued command. Without it a command endpoint that is down is contacted once
     * per queued command on every tick, and - worse - every failed attempt consumes the retry budget, so a
     * short outage dead-letters commands that were perfectly deliverable. Package visible so a test can
     * inject one.
     */
    @Nullable
    volatile CircuitBreaker deliveryBreaker;

    /**
     * Constructor with mandatory data.
     *
     * @param outboxService     Service used to read and update the outbox.
     * @param commandRestClient Client used to deliver commands to the command endpoint.
     * @param config            Configuration (batch size, max retries, ...).
     * @param transactionManager Transaction manager used to open per-command transactions.
     */
    public CommandQueueExecutor(final CommandOutboxService outboxService,
                                final CommandRestClient commandRestClient,
                                final CommandQueueConfig config,
                                final PlatformTransactionManager transactionManager) {
        super();
        Contract.requireArgNotNull("outboxService", outboxService);
        Contract.requireArgNotNull("commandRestClient", commandRestClient);
        Contract.requireArgNotNull("config", config);
        Contract.requireArgNotNull("transactionManager", transactionManager);
        this.outboxService = outboxService;
        this.commandRestClient = commandRestClient;
        this.config = config;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reads a batch of queued commands and delivers them. Scheduled via the cron expression from
     * {@link CommandQueueConfig}. Overlapping runs are skipped.
     */
    @Scheduled(cron = "${" + CommandQueueConfig.PREFIX + ".cron:" + CommandQueueConfig.DEFAULT_CRON + "}")
    public void drain() {
        if (!lock.tryLock()) {
            LOG.trace("Previous drain still running - skipping");
            return;
        }
        try {
            final List<Entry> batch = requiresNewTransaction.execute(status -> outboxService.fetchBatch(config.getBatchSize()));
            if (batch == null || batch.isEmpty()) {
                return;
            }
            LOG.debug("Draining {} queued command(s)", batch.size());
            for (final Entry entry : batch) {
                if (!deliver(entry)) {
                    // Endpoint is known to be down: stop the run and leave the rest of the batch queued.
                    LOG.debug("Command endpoint unavailable - deferring the rest of the batch");
                    break;
                }
            }
        } catch (final RuntimeException ex) { // NOSONAR - a failing run must not kill the scheduler
            LOG.error("Error draining the command outbox", ex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Delivers a single queued command.
     *
     * @param entry Command to deliver.
     * @return {@literal false} if the endpoint is known to be unavailable and the rest of the batch should be
     *         deferred, {@literal true} otherwise (delivered, or failed in a way that was recorded).
     */
    private boolean deliver(final Entry entry) {
        try {
            circuitBreaker().executeCallable(() -> {
                commandRestClient.cmd(entry.type(), entry.contentType(), entry.json());
                return null;
            });
            requiresNewTransaction.executeWithoutResult(status -> outboxService.delete(entry.id()));
            LOG.debug("Delivered command '{}' ({})", entry.id(), entry.type());
            return true;
        } catch (final CallNotPermittedException ex) {
            // Deliberately NO recordFailure: the command was never sent, so this must not count towards
            // the dead-letter budget. It stays queued and is tried again once the breaker half-opens.
            return false;
        } catch (final Exception ex) { // NOSONAR - the guarded call declares Exception
            LOG.warn("Failed to deliver command '{}' ({}): {}", entry.id(), entry.type(), ex.getMessage());
            requiresNewTransaction.executeWithoutResult(
                    status -> outboxService.recordFailure(entry.id(), ex.getMessage(), config.getMaxRetries()));
            return true;
        }
    }

    /**
     * Returns the delivery circuit breaker, created lazily because the configuration is injected after
     * construction.
     *
     * @return Breaker shared by all deliveries - it is the endpoint that is down, not a single command.
     */
    CircuitBreaker circuitBreaker() {
        CircuitBreaker result = deliveryBreaker;
        if (result == null) {
            synchronized (this) {
                result = deliveryBreaker;
                if (result == null) {
                    result = CircuitBreaker.of("cqrs4j-command-delivery", CircuitBreakerConfig.custom()
                            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                            .slidingWindowSize(config.getBreakerWindowSize())
                            .minimumNumberOfCalls(config.getBreakerWindowSize())
                            .failureRateThreshold(config.getBreakerFailureRatePercent())
                            .waitIntervalFunctionInOpenState(IntervalFunction.ofExponentialBackoff(
                                    config.getBreakerInitialWait(), 2.0, config.getBreakerMaxWait()))
                            // A rejected command (unknown type, validation error) is not an outage and
                            // must never open the breaker for all the other commands.
                            .recordException(CqrsUtils::isTransientInfrastructureFailure)
                            .build());
                    deliveryBreaker = result;
                }
            }
        }
        return result;
    }

}
