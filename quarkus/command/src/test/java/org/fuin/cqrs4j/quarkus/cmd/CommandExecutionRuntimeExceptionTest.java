package org.fuin.cqrs4j.quarkus.cmd;

import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CommandExecutionRuntimeException}.
 */
class CommandExecutionRuntimeExceptionTest {

    @Test
    void testWrapsCauseAndMessage() {

        final CommandExecutionFailedException cause =
                new CommandExecutionFailedException(new IllegalStateException("Boom"));

        final CommandExecutionRuntimeException testee = new CommandExecutionRuntimeException(cause);

        // The message is carried over so the failure is readable without unwrapping...
        assertThat(testee.getMessage()).isEqualTo(cause.getMessage());
        // ...and the original stays reachable for a caller that wants to handle it.
        assertThat(testee.getCause()).isSameAs(cause);
    }

}
