package org.fuin.cqrs4j.quarkus.pm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProcessManagerCommandDeadLetter} class.
 */
class ProcessManagerCommandDeadLetterTest {

    @Test
    void testConstructorAndGetters() {
        final ProcessManagerCommandDeadLetter dl = new ProcessManagerCommandDeadLetter("id-1", "MyCommand", "{}", 1L, 9L, 5, "boom");
        assertThat(dl.getId()).isEqualTo("id-1");
        assertThat(dl.getType()).isEqualTo("MyCommand");
        assertThat(dl.getJson()).isEqualTo("{}");
        assertThat(dl.getCreatedTs()).isEqualTo(1L);
        assertThat(dl.getFailedTs()).isEqualTo(9L);
        assertThat(dl.getRetries()).isEqualTo(5);
        assertThat(dl.getError()).isEqualTo("boom");
    }

    @Test
    void testFromOutbox() {
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-1", "MyCommand", "{}", 1L);
        outbox.recordFailure("boom");

        final ProcessManagerCommandDeadLetter dl = ProcessManagerCommandDeadLetter.fromOutbox(outbox, 9L);

        assertThat(dl.getId()).isEqualTo("id-1");
        assertThat(dl.getType()).isEqualTo("MyCommand");
        assertThat(dl.getJson()).isEqualTo("{}");
        assertThat(dl.getCreatedTs()).isEqualTo(1L);
        assertThat(dl.getFailedTs()).isEqualTo(9L);
        assertThat(dl.getRetries()).isEqualTo(1);
        assertThat(dl.getError()).isEqualTo("boom");
    }

}
