package org.fuin.cqrs4j.core;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Application-provided handler invoked by the process-timeout sweeper when a process timeout becomes due (the
 * awaited reply never arrived, i.e. a potential zombie). The application decides how to react - compensate,
 * re-emit the awaited command, escalate, or simply drop a stale timeout from a superseded process version.
 * <p>
 * The handler runs inside the sweeper's transaction, together with removal of the timeout, so any command it
 * {@code enqueue}s or timeout it re-arms commits atomically with consuming the due timeout. It must be written
 * <b>idempotently</b>: a missed {@link ProcessTimeoutService#cancel(String)} (e.g. a race with the reply) can
 * still cause a due timeout to fire, so the handler should check current business state before acting.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface ProcessTimeoutHandler {

    /**
     * Handles a due process timeout.
     *
     * @param timeout The timeout that became due.
     */
    void onTimeout(@NotNull DueProcessTimeout timeout);

    /**
     * A process timeout that has become due, handed to the {@link ProcessTimeoutHandler}.
     *
     * @param processId      Unique process instance id.
     * @param processType    Process type name.
     * @param processVersion Process (definition) version the timeout was armed with.
     * @param deadlineTs     Epoch millisecond deadline that elapsed.
     * @param payload        Optional application data supplied when the timeout was armed.
     * @param retries        Number of times handling this timeout has already failed.
     */
    record DueProcessTimeout(String processId, String processType, int processVersion, long deadlineTs,
                             @Nullable String payload, int retries) {
    }

}
