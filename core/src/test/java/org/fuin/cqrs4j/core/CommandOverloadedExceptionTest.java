package org.fuin.cqrs4j.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CommandOverloadedException} class.
 */
class CommandOverloadedExceptionTest {

    @Test
    void testCreate() {

        // PREPARE
        final IOException ex = new IOException("Whatever");

        // TEST
        final CommandOverloadedException testee = new CommandOverloadedException("Too busy", ex);

        // VERIFY
        assertThat(testee.getMessage()).isEqualTo("Too busy");
        assertThat(testee.getCause()).isEqualTo(ex);
    }

    @Test
    void testCreateWithoutCause() {
        assertThat(new CommandOverloadedException("Too busy", null).getCause()).isNull();
    }

    @Test
    void testIsUnchecked() {
        // The dispatch path declares no checked exception for this, and the REST layers map it to 503.
        assertThat(RuntimeException.class).isAssignableFrom(CommandOverloadedException.class);
    }

}
