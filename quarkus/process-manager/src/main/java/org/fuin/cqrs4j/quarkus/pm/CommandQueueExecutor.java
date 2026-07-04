package org.fuin.cqrs4j.quarkus.pm;

import io.quarkus.scheduler.Scheduled;
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
            outboxService.delete(entry.id());
            LOG.debug("Delivered command '{}' ({})", entry.id(), entry.type());
        } catch (final RuntimeException ex) {
            LOG.warn("Failed to deliver command '{}' ({}): {}", entry.id(), entry.type(), ex.getMessage());
            outboxService.recordFailure(entry.id(), ex.getMessage(), config.getMaxRetries());
        }
    }

}
