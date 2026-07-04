package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.cqrs4j.springboot.pm.core.CommandOutboxService.Entry;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
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
                deliver(entry);
            }
        } catch (final RuntimeException ex) { // NOSONAR - a failing run must not kill the scheduler
            LOG.error("Error draining the command outbox", ex);
        } finally {
            lock.unlock();
        }
    }

    private void deliver(final Entry entry) {
        try {
            commandRestClient.cmd(entry.type(), entry.contentType(), entry.json());
            requiresNewTransaction.executeWithoutResult(status -> outboxService.delete(entry.id()));
            LOG.debug("Delivered command '{}' ({})", entry.id(), entry.type());
        } catch (final RuntimeException ex) {
            LOG.warn("Failed to deliver command '{}' ({}): {}", entry.id(), entry.type(), ex.getMessage());
            requiresNewTransaction.executeWithoutResult(
                    status -> outboxService.recordFailure(entry.id(), ex.getMessage(), config.getMaxRetries()));
        }
    }

}
