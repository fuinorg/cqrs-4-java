package org.fuin.cqrs4j.core;

import jakarta.validation.constraints.NotNull;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

/**
 * Registers and cancels <em>process timeouts</em> so a process manager that awaits a reply cannot become a
 * zombie. A process manager <b>arms</b> a timeout when it starts a step that expects a reply and <b>cancels</b>
 * it when the reply arrives; if the deadline passes without a cancel, a scheduled sweeper hands the timeout to a
 * {@link ProcessTimeoutHandler} so the application can compensate, retry, or escalate.
 * <p>
 * {@link #arm(String, String, int, long, String)} and {@link #cancel(String)} are meant to be called <b>inside
 * the process manager view's transaction</b> (exactly like {@link CommandOutbox#enqueue(Command)}), so arming
 * commits atomically with the emitted command and the state change, and cancelling commits atomically with
 * processing the reply.
 * <p>
 * There is one pending timeout per {@code processId} (the primary key); arming again replaces the pending
 * deadline. All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface ProcessTimeoutService {

    /**
     * Arms (or re-arms) the pending timeout for a process. If the deadline is reached before {@link #cancel(String)}
     * is called, the sweeper dispatches it to the {@link ProcessTimeoutHandler}.
     *
     * @param processId      Unique process instance id (primary key; one pending timeout per process).
     * @param processType    Process type name (lets a handler route by kind of process).
     * @param processVersion Process (definition) version, so a new version can discriminate stale timeouts.
     * @param deadlineTs     Epoch millisecond time at which the timeout becomes due.
     * @param payload        Optional application data carried to the handler (e.g. what the process awaits).
     */
    void arm(@NotNull String processId, @NotNull String processType, int processVersion, long deadlineTs,
             @Nullable String payload);

    /**
     * Cancels the pending timeout for a process (the awaited reply arrived). A no-op if none is pending.
     *
     * @param processId Unique process instance id.
     */
    void cancel(@NotNull String processId);

}
