package org.fuin.cqrs4j.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CommandExecutionFailedException} class.
 */
class CommandExecutionFailedExceptionTest {

    @Test
    void testCreate() {

        // PREPARE
        final IOException ex = new IOException("Whatever");

        // TEST
        CommandExecutionFailedException testee = new CommandExecutionFailedException(ex);

        // VERIFY
        assertThat(testee.getCause()).isEqualTo(ex);

    }

}
