package org.fuin.cqrs4j.springboot.command.core;

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

        assertThat(testee.getMessage()).isEqualTo(cause.getMessage());
        assertThat(testee.getCause()).isSameAs(cause);
    }

}
