package org.fuin.cqrs4j.jpa.pm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProcessManagerCommandOutbox} class.
 */
class ProcessManagerCommandOutboxTest {

    @Test
    void testConstructorAndGetters() {
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-1", "MyCommand", "2", "{\"a\":1}", 123L);
        assertThat(outbox.getId()).isEqualTo("id-1");
        assertThat(outbox.getType()).isEqualTo("MyCommand");
        assertThat(outbox.getVersion()).isEqualTo("2");
        assertThat(outbox.getJson()).isEqualTo("{\"a\":1}");
        assertThat(outbox.getCreatedTs()).isEqualTo(123L);
        assertThat(outbox.getRetries()).isZero();
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    void testNullVersion() {
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-1", "MyCommand", null, "{}", 1L);
        assertThat(outbox.getVersion()).isNull();
    }

    @Test
    void testRecordFailure() {
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-1", "MyCommand", null, "{}", 1L);

        outbox.recordFailure("boom");
        assertThat(outbox.getRetries()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("boom");

        outbox.recordFailure("bang");
        assertThat(outbox.getRetries()).isEqualTo(2);
        assertThat(outbox.getLastError()).isEqualTo("bang");
    }

}
