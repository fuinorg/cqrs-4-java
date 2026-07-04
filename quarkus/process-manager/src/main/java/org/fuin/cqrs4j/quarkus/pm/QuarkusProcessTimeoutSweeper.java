package org.fuin.cqrs4j.quarkus.pm;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler.DueProcessTimeout;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sweeps the process-timeout table: on every (cron scheduled) run it reads a batch of timeouts whose deadline has
 * passed and hands each to the application's {@link ProcessTimeoutHandler}. A successfully handled timeout is
 * removed; a failing one has its retry counter incremented and is moved to the dead-letter table once it exhausts
 * {@link ProcessTimeoutConfig#getMaxRetries()} attempts. This turns an unanswered process step into a detected,
 * repairable event rather than a zombie.
 * <p>
 * Each due timeout is handled together with its removal in one {@code REQUIRES_NEW} transaction, so any command
 * the handler enqueues or timeout it re-arms commits atomically with consuming the due timeout, and one failing
 * timeout does not roll back its siblings. A {@link ReentrantLock} guards against overlapping runs. If no unique
 * {@link ProcessTimeoutHandler} bean is present the sweep is inactive (armed timeouts then surface via the
 * metrics gauge rather than being silently dropped).
 */
@ThreadSafe
@ApplicationScoped
public class QuarkusProcessTimeoutSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusProcessTimeoutSweeper.class);

    @Inject
    QuarkusProcessTimeoutRepository repository;

    @Inject
    ProcessTimeoutConfig config;

    @Inject
    Instance<ProcessTimeoutHandler> handlers;

    private final Lock lock = new ReentrantLock();

    /**
     * Reads a batch of due timeouts and dispatches them to the handler. Scheduled via
     * {@code org.fuin.cqrs4j.pm.timeout.cron}. Overlapping runs are skipped, and the run is a no-op when no
     * unique handler is registered.
     */
    @Scheduled(cron = "{" + ProcessTimeoutConfig.PREFIX + ".cron:" + ProcessTimeoutConfig.DEFAULT_CRON + "}")
    void drain() {
        if (!handlers.isResolvable()) {
            LOG.trace("No unique ProcessTimeoutHandler - skipping timeout sweep");
            return;
        }
        if (!lock.tryLock()) {
            LOG.trace("Previous sweep still running - skipping");
            return;
        }
        try {
            final ProcessTimeoutHandler handler = handlers.get();
            final List<DueProcessTimeout> batch = repository.fetchDue(now(), config.getBatchSize());
            if (batch.isEmpty()) {
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
            runInNewTransaction(() -> {
                handler.onTimeout(due);
                repository.delete(due.processId());
            });
            LOG.debug("Handled due timeout for process '{}' ({})", due.processId(), due.processType());
        } catch (final RuntimeException ex) {
            LOG.warn("Failed to handle timeout for process '{}' ({}): {}", due.processId(), due.processType(),
                    ex.getMessage());
            runInNewTransaction(
                    () -> repository.recordFailure(due.processId(), ex.getMessage(), config.getMaxRetries()));
        }
    }

    /**
     * Runs the given action in a new transaction. Overridable for testing.
     *
     * @param action Action to run transactionally.
     */
    protected void runInNewTransaction(final Runnable action) {
        QuarkusTransaction.requiringNew().run(action);
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
