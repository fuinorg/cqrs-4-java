package org.fuin.cqrs4j.core;

import org.fuin.objects4j.common.NotThreadSafe;
import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Signals that a queued command could not be delivered to the command endpoint.
 * <p>
 * <b>This is the permanent variant: delivering it again cannot succeed.</b> The endpoint answered, and the
 * answer says the command itself is the problem (an unknown command type, a validation error, a rejected
 * authentication). Retrying only burns the retry budget until the command is dead-lettered.
 * <p>
 * Use {@link TransientCommandDeliveryException} when the endpoint could not be reached or answered that it
 * is temporarily unable to handle the request.
 */
@NotThreadSafe
public class CommandDeliveryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1000L;

    private final int statusCode;

    /**
     * Constructor with message and status code.
     *
     * @param message    Description of the problem.
     * @param statusCode HTTP status the endpoint answered with, or {@literal 0} if there was no answer.
     * @param cause      Original failure (may be {@literal null}).
     */
    public CommandDeliveryException(final String message, final int statusCode, @Nullable final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status the endpoint answered with.
     *
     * @return Status code, or {@literal 0} if the endpoint could not be reached at all.
     */
    public int getStatusCode() {
        return statusCode;
    }

}
