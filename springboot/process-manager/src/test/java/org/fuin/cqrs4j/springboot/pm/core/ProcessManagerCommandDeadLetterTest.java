package org.fuin.cqrs4j.springboot.pm.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProcessManagerCommandDeadLetter} class.
 */
class ProcessManagerCommandDeadLetterTest {

    @Test
    void testConstructorAndGetters() {
        final ProcessManagerCommandDeadLetter dl = new ProcessManagerCommandDeadLetter("id-1", "MyCommand", "{}", 1L, 2L, 5, "boom");
        assertThat(dl.getId()).isEqualTo("id-1");
        assertThat(dl.getType()).isEqualTo("MyCommand");
        assertThat(dl.getJson()).isEqualTo("{}");
        assertThat(dl.getCreatedTs()).isEqualTo(1L);
        assertThat(dl.getFailedTs()).isEqualTo(2L);
        assertThat(dl.getRetries()).isEqualTo(5);
        assertThat(dl.getError()).isEqualTo("boom");
    }

    @Test
    void testFromOutbox() {
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-2", "OtherCommand", "{\"x\":1}", 100L);
        outbox.recordFailure("failed-1");
        outbox.recordFailure("failed-2");

        final ProcessManagerCommandDeadLetter dl = ProcessManagerCommandDeadLetter.fromOutbox(outbox, 999L);

        assertThat(dl.getId()).isEqualTo("id-2");
        assertThat(dl.getType()).isEqualTo("OtherCommand");
        assertThat(dl.getJson()).isEqualTo("{\"x\":1}");
        assertThat(dl.getCreatedTs()).isEqualTo(100L);
        assertThat(dl.getFailedTs()).isEqualTo(999L);
        assertThat(dl.getRetries()).isEqualTo(2);
        assertThat(dl.getError()).isEqualTo("failed-2");
    }

}
