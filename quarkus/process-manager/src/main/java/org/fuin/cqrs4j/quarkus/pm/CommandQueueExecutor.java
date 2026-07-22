package org.fuin.cqrs4j.quarkus.pm;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.faulttolerance.api.Guard;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.jspecify.annotations.Nullable;

import java.time.temporal.ChronoUnit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.quarkus.pm.CommandOutboxService.Entry;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Drains the command outbox: on every (cron scheduled) run it reads a batch of queued commands and POSTs each one
 * to the configured command endpoint. A successfully delivered command is deleted from the outbox; a failed one
 * has its retry counter incremented and is moved to the dead-letter table once it exhausts
 * {@link CommandQueueConfig#getMaxRetries()} attempts.
 * <p>
 * The HTTP call happens outside any database transaction; each resulting outbox mutation runs in its own
 * {@code REQUIRES_NEW} transaction (on {@link CommandOutboxService}) so that one failing command does not
 * roll back the successful delivery of its siblings. A {@link ReentrantLock} guards against overlapping runs.
 */
@ThreadSafe
@ApplicationScoped
public class CommandQueueExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(CommandQueueExecutor.class);

    @Inject
    CommandOutboxService outboxService;

    @Inject
    CommandRestClient commandRestClient;

    @Inject
    CommandQueueConfig config;

    private final Lock lock = new ReentrantLock();

    /**
     * Guards the delivery of a queued command. Without it a command endpoint that is down is contacted once
     * per queued command on every tick, and - worse - every failed attempt consumes the retry budget, so a
     * short outage dead-letters commands that were perfectly deliverable. While the breaker is open the
     * batch is deferred untouched instead.
     */
    // Package visible so a test can inject a pass-through; guard() honours a pre-set instance. Creating a
    // real Guard needs the SmallRye Fault Tolerance runtime SPI, which only exists inside the container.
    @Nullable
    volatile Guard deliveryGuard;

    /**
     * Reads a batch of queued commands and delivers them. Scheduled via the cron expression from
     * {@code org.fuin.cqrs4j.pm.cmdqueue.cron}. Overlapping runs are skipped.
     */
    @Scheduled(cron = "{" + CommandQueueConfig.PREFIX + ".cron:" + CommandQueueConfig.DEFAULT_CRON + "}")
    void drain() {
        if (!lock.tryLock()) {
            LOG.trace("Previous drain still running - skipping");
            return;
        }
        try {
            final List<Entry> batch = outboxService.fetchBatch(config.getBatchSize());
            if (batch.isEmpty()) {
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
            guard().call(() -> {
                commandRestClient.cmd(entry.type(), entry.contentType(), entry.json());
                return null;
            }, Void.class);
            outboxService.delete(entry.id());
            LOG.debug("Delivered command '{}' ({})", entry.id(), entry.type());
            return true;
        } catch (final CircuitBreakerOpenException ex) {
            // Deliberately NO recordFailure: the command was never sent, so this must not count towards
            // the dead-letter budget. It stays queued and is tried again once the breaker half-opens.
            return false;
        } catch (final Exception ex) { // NOSONAR - the guarded call declares Exception
            LOG.warn("Failed to deliver command '{}' ({}): {}", entry.id(), entry.type(), ex.getMessage());
            outboxService.recordFailure(entry.id(), ex.getMessage(), config.getMaxRetries());
            return true;
        }
    }

    /**
     * Returns the delivery guard, created lazily because the configuration is injected after construction.
     *
     * @return Guard shared by all deliveries - it is the endpoint that is down, not a single command.
     */
    private Guard guard() {
        Guard result = deliveryGuard;
        if (result == null) {
            synchronized (this) {
                result = deliveryGuard;
                if (result == null) {
                    result = Guard.create()
                            .withDescription("cqrs4j-command-delivery")
                            .withCircuitBreaker()
                            // A rejected command (unknown type, validation error) is not an outage and
                            // must never open the breaker for all the other commands.
                            .when(CqrsUtils::isTransientInfrastructureFailure)
                            .requestVolumeThreshold(config.getBreakerRequestVolumeThreshold())
                            .failureRatio(config.getBreakerFailureRatio())
                            .delay(config.getBreakerDelay(), ChronoUnit.MILLIS)
                            .onStateChange(state -> LOG.info("Command delivery circuit breaker is now {}", state))
                            .done()
                            .build();
                    deliveryGuard = result;
                }
            }
        }
        return result;
    }

}
