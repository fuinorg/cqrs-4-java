package org.fuin.cqrs4j.core;

import org.fuin.objects4j.common.NotThreadSafe;
import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Signals that the receiver refused to start handling a command because it is already running as many as it
 * is allowed to - the bulkhead around the inbound deduplication is full.
 * <p>
 * <b>The command was not executed.</b> It is refused before any handler runs and before anything is written,
 * so redelivering it is safe and is exactly what should happen. Receivers throw this instead of queueing the
 * request behind a struggling database, because a request thread waiting on a slow dedup lookup is a request
 * thread that cannot serve anything else; shedding the load keeps the endpoint responsive for the traffic it
 * can still handle.
 * <p>
 * The REST layers map it to <b>HTTP 503</b>. That matters for the sender: its outbox classifies a 5xx as a
 * {@link TransientCommandDeliveryException}, so the batch is deferred and redelivered instead of counting
 * towards the dead-letter budget. A 4xx would permanently dead-letter a command that is perfectly valid and
 * was simply turned away.
 * <p>
 * This is deliberately distinct from {@link CommandExecutionFailedException}, which means a command <em>was</em>
 * dispatched and something about handling it went wrong.
 */
@NotThreadSafe
public final class CommandOverloadedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1000L;

    /**
     * Constructor with message and cause.
     *
     * @param message Description of the problem.
     * @param cause   Original failure raised by the bulkhead (may be {@literal null}).
     */
    public CommandOverloadedException(final String message, @Nullable final Throwable cause) {
        super(message, cause);
    }

}
