package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.cqrs4j.core.ProcessTimeoutHandler;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler.DueProcessTimeout;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sweeps the process-timeout table: on every (cron scheduled) run it reads a batch of timeouts whose deadline
 * has passed and hands each to the application's {@link ProcessTimeoutHandler}. A successfully handled timeout is
 * removed; a failing one has its retry counter incremented and is moved to the dead-letter table once it exhausts
 * {@link ProcessTimeoutConfig#getMaxRetries()} attempts. This is what turns an unanswered process step into a
 * detected, repairable event rather than a zombie.
 * <p>
 * Each due timeout is handled together with its removal in one
 * {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} transaction, so any command the handler enqueues or
 * timeout it re-arms commits atomically with consuming the due timeout, and one failing timeout does not roll back
 * its siblings. A {@link ReentrantLock} guards against overlapping runs. If no unique
 * {@link ProcessTimeoutHandler} bean is present the sweep is inactive (armed timeouts then surface via the
 * metrics gauge rather than being silently dropped).
 */
@ThreadSafe
@Component
public class ProcessTimeoutSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessTimeoutSweeper.class);

    private final ProcessTimeoutRepository repository;

    private final ProcessTimeoutConfig config;

    private final ObjectProvider<ProcessTimeoutHandler> handlers;

    private final TransactionTemplate requiresNewTransaction;

    private final Lock lock = new ReentrantLock();

    /**
     * Constructor with mandatory data.
     *
     * @param repository         Repository used to read and update the timeout tables.
     * @param config             Configuration (batch size, max retries, cron).
     * @param handlers           Application-provided timeout handler(s); the sweep is inactive unless exactly one
     *                           is present.
     * @param transactionManager Transaction manager used to open per-timeout transactions.
     */
    public ProcessTimeoutSweeper(final ProcessTimeoutRepository repository,
                                 final ProcessTimeoutConfig config,
                                 final ObjectProvider<ProcessTimeoutHandler> handlers,
                                 final PlatformTransactionManager transactionManager) {
        super();
        Contract.requireArgNotNull("repository", repository);
        Contract.requireArgNotNull("config", config);
        Contract.requireArgNotNull("handlers", handlers);
        Contract.requireArgNotNull("transactionManager", transactionManager);
        this.repository = repository;
        this.config = config;
        this.handlers = handlers;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reads a batch of due timeouts and dispatches them to the handler. Scheduled via the cron expression from
     * {@link ProcessTimeoutConfig}. Overlapping runs are skipped, and the run is a no-op when no unique handler
     * is registered.
     */
    @Scheduled(cron = "${" + ProcessTimeoutConfig.PREFIX + ".cron:" + ProcessTimeoutConfig.DEFAULT_CRON + "}")
    public void drain() {
        final ProcessTimeoutHandler handler = handlers.getIfUnique();
        if (handler == null) {
            LOG.trace("No unique ProcessTimeoutHandler - skipping timeout sweep");
            return;
        }
        if (!lock.tryLock()) {
            LOG.trace("Previous sweep still running - skipping");
            return;
        }
        try {
            final long now = now();
            final List<DueProcessTimeout> batch = requiresNewTransaction.execute(
                    status -> repository.fetchDue(now, config.getBatchSize()));
            if (batch == null || batch.isEmpty()) {
                return;
            }
            LOG.debug("Sweeping {} due process timeout(s)", batch.size());
            for (final DueProcessTimeout due : batch) {
                handle(handler, due);
            }
        } catch (final RuntimeException ex) { // NOSONAR - a failing run must not kill the scheduler
            LOG.error("Error sweeping process timeouts", ex);
        } finally {
            lock.unlock();
        }
    }

    private void handle(final ProcessTimeoutHandler handler, final DueProcessTimeout due) {
        try {
            requiresNewTransaction.executeWithoutResult(status -> {
                handler.onTimeout(due);
                repository.delete(due.processId());
            });
            LOG.debug("Handled due timeout for process '{}' ({})", due.processId(), due.processType());
        } catch (final RuntimeException ex) {
            LOG.warn("Failed to handle timeout for process '{}' ({}): {}", due.processId(), due.processType(),
                    ex.getMessage());
            requiresNewTransaction.executeWithoutResult(
                    status -> repository.recordFailure(due.processId(), ex.getMessage(), config.getMaxRetries()));
        }
    }

    /**
     * Returns the current time in epoch milliseconds. Overridable for testing.
     *
     * @return Current time.
     */
    protected long now() {
        return System.currentTimeMillis();
    }

}
