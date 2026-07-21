package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.objects4j.common.NotThreadSafe;

import java.io.Serial;

/**
 * Unchecked wrapper for a {@link CommandExecutionFailedException}. It exists so the command endpoint
 * can keep the signature of {@link CommandRestControllerApi}, which declares no checked exception
 * because a client proxy has nothing to catch.
 */
@NotThreadSafe
public class CommandExecutionRuntimeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1000L;

    /**
     * Constructor with the wrapped failure.
     *
     * @param cause Failure that occurred while executing the command.
     */
    public CommandExecutionRuntimeException(final CommandExecutionFailedException cause) {
        super(cause.getMessage(), cause);
    }

}
