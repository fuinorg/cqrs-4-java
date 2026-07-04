package org.fuin.cqrs4j.quarkus.pm;
import org.fuin.cqrs4j.jpa.pm.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler.DueProcessTimeout;
import org.fuin.cqrs4j.core.ProcessTimeoutService;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Reads and writes the process-timeout and timeout dead-letter tables. Arming/cancelling a timeout
 * ({@link #arm(String, String, int, long, String)} / {@link #cancel(String)}) is meant to happen inside the
 * process manager view's transaction so it commits atomically with the process manager state. {@link #delete(String)}
 * and {@link #recordFailure(String, String, int)} run inside the transaction the {@link ProcessTimeoutSweeper}
 * opens (so handling and removal commit together); {@link #fetchDue(long, int)} and the count methods open their own.
 */
@ThreadSafe
@ApplicationScoped
public class ProcessTimeoutRepository implements ProcessTimeoutService {

    private static final String ARG_PROCESS_ID = "processId";

    @Inject
    EntityManager em;

    @Override
    public void arm(@NotNull final String processId, @NotNull final String processType, final int processVersion,
                    final long deadlineTs, @Nullable final String payload) {
        Contract.requireArgNotNull(ARG_PROCESS_ID, processId);
        Contract.requireArgNotNull("processType", processType);
        final ProcessManagerTimeout existing = em.find(ProcessManagerTimeout.class, processId);
        if (existing == null) {
            em.persist(new ProcessManagerTimeout(processId, processType, processVersion, deadlineTs, payload,
                    System.currentTimeMillis()));
        } else {
            existing.rearm(processType, processVersion, deadlineTs, payload);
        }
    }

    @Override
    public void cancel(@NotNull final String processId) {
        Contract.requireArgNotNull(ARG_PROCESS_ID, processId);
        final ProcessManagerTimeout timeout = em.find(ProcessManagerTimeout.class, processId);
        if (timeout != null) {
            em.remove(timeout);
        }
    }

    /**
     * Reads the oldest due timeouts (deadline reached, by deadline) up to the given maximum.
     *
     * @param now Current epoch millisecond time; a timeout is due when its deadline is at or before this.
     * @param max Maximum number of entries to read.
     * @return Due timeouts, earliest deadline first (never {@literal null}, but may be empty).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<DueProcessTimeout> fetchDue(final long now, final int max) {
        return em.createQuery(
                        "SELECT t FROM ProcessManagerTimeout t WHERE t.deadlineTs <= :now "
                                + "ORDER BY t.deadlineTs ASC, t.processId ASC", ProcessManagerTimeout.class)
                .setParameter("now", now)
                .setMaxResults(max)
                .getResultList()
                .stream()
                .map(t -> new DueProcessTimeout(t.getProcessId(), t.getProcessType(), t.getProcessVersion(),
                        t.getDeadlineTs(), t.getPayload(), t.getRetries()))
                .toList();
    }

    /**
     * Removes a successfully handled timeout. Runs in the caller's (sweeper) transaction.
     *
     * @param processId Identifier of the process whose timeout to remove.
     */
    public void delete(final String processId) {
        Contract.requireArgNotNull(ARG_PROCESS_ID, processId);
        final ProcessManagerTimeout timeout = em.find(ProcessManagerTimeout.class, processId);
        if (timeout != null) {
            em.remove(timeout);
        }
    }

    /**
     * Records a failed handling attempt. The retry counter is incremented; once it reaches {@code maxRetries} the
     * timeout is moved to the dead-letter table and removed. Runs in the caller's (sweeper) transaction.
     *
     * @param processId  Identifier of the process whose timeout handling failed.
     * @param error      Error message describing the failure.
     * @param maxRetries Maximum number of attempts before the timeout is dead-lettered.
     */
    public void recordFailure(final String processId, @Nullable final String error, final int maxRetries) {
        Contract.requireArgNotNull(ARG_PROCESS_ID, processId);
        final ProcessManagerTimeout timeout = em.find(ProcessManagerTimeout.class, processId);
        if (timeout == null) {
            return;
        }
        timeout.recordFailure(error);
        if (timeout.getRetries() >= maxRetries) {
            em.persist(ProcessManagerTimeoutDeadLetter.fromTimeout(timeout, System.currentTimeMillis()));
            em.remove(timeout);
        }
    }

    /**
     * Returns the number of pending timeouts (armed and not yet due or handled). Runs in its own transaction so
     * it can be called from outside a transaction (e.g. a metrics gauge).
     *
     * @return Current pending timeout count.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long pendingCount() {
        return em.createQuery("SELECT COUNT(t) FROM ProcessManagerTimeout t", Long.class).getSingleResult();
    }

    /**
     * Returns the number of overdue timeouts (deadline reached but not yet handled). Runs in its own transaction
     * so it can be called from outside a transaction.
     *
     * @param now Current epoch millisecond time.
     * @return Current overdue timeout count.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long overdueCount(final long now) {
        return em.createQuery("SELECT COUNT(t) FROM ProcessManagerTimeout t WHERE t.deadlineTs <= :now", Long.class)
                .setParameter("now", now)
                .getSingleResult();
    }

}
