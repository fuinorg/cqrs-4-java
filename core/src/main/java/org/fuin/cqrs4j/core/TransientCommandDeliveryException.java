package org.fuin.cqrs4j.core;

import org.fuin.objects4j.common.NotThreadSafe;
import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Signals that a queued command could not be delivered because the command endpoint could not be reached, did
 * not answer in time, or answered that it is temporarily unable to handle the request.
 * <p>
 * <b>The contract is "transient": delivering the command again may well succeed.</b> A retry must therefore
 * not count towards the dead-letter budget in the same way a permanent failure does - a five minute outage
 * would otherwise dead-letter perfectly valid commands.
 */
@NotThreadSafe
public final class TransientCommandDeliveryException extends CommandDeliveryException {

    @Serial
    private static final long serialVersionUID = 1000L;

    /**
     * Constructor with message and status code.
     *
     * @param message    Description of the problem.
     * @param statusCode HTTP status the endpoint answered with, or {@literal 0} if there was no answer.
     * @param cause      Original failure (may be {@literal null}).
     */
    public TransientCommandDeliveryException(final String message, final int statusCode,
                                             @Nullable final Throwable cause) {
        super(message, statusCode, cause);
    }

}
