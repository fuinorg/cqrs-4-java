package org.fuin.cqrs4j.quarkus.pm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProcessManagerTimeout} class.
 */
class ProcessManagerTimeoutTest {

    @Test
    void testConstructorAndGetters() {
        final ProcessManagerTimeout timeout = new ProcessManagerTimeout("p-1", "OrderProcess", 2, 5000L, "await-ack", 100L);
        assertThat(timeout.getProcessId()).isEqualTo("p-1");
        assertThat(timeout.getProcessType()).isEqualTo("OrderProcess");
        assertThat(timeout.getProcessVersion()).isEqualTo(2);
        assertThat(timeout.getDeadlineTs()).isEqualTo(5000L);
        assertThat(timeout.getPayload()).isEqualTo("await-ack");
        assertThat(timeout.getCreatedTs()).isEqualTo(100L);
        assertThat(timeout.getRetries()).isZero();
        assertThat(timeout.getLastError()).isNull();
    }

    @Test
    void testRearmResetsDeadlineAndRetries() {
        final ProcessManagerTimeout timeout = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        timeout.recordFailure("boom");

        timeout.rearm("OrderProcess", 3, 9000L, "await-ship");

        assertThat(timeout.getProcessVersion()).isEqualTo(3);
        assertThat(timeout.getDeadlineTs()).isEqualTo(9000L);
        assertThat(timeout.getPayload()).isEqualTo("await-ship");
        assertThat(timeout.getRetries()).isZero();
        assertThat(timeout.getLastError()).isNull();
    }

    @Test
    void testRecordFailure() {
        final ProcessManagerTimeout timeout = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);

        timeout.recordFailure("boom");
        assertThat(timeout.getRetries()).isEqualTo(1);
        assertThat(timeout.getLastError()).isEqualTo("boom");

        timeout.recordFailure("bang");
        assertThat(timeout.getRetries()).isEqualTo(2);
        assertThat(timeout.getLastError()).isEqualTo("bang");
    }

}
