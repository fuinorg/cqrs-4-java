package org.fuin.cqrs4j.springboot.pm.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProcessManagerTimeoutDeadLetter} class.
 */
class ProcessManagerTimeoutDeadLetterTest {

    @Test
    void testConstructorAndGetters() {
        final ProcessManagerTimeoutDeadLetter dl = new ProcessManagerTimeoutDeadLetter("p-1", "OrderProcess", 2, 5000L,
                "await-ack", 100L, 9000L, 5, "boom");
        assertThat(dl.getProcessId()).isEqualTo("p-1");
        assertThat(dl.getProcessType()).isEqualTo("OrderProcess");
        assertThat(dl.getProcessVersion()).isEqualTo(2);
        assertThat(dl.getDeadlineTs()).isEqualTo(5000L);
        assertThat(dl.getPayload()).isEqualTo("await-ack");
        assertThat(dl.getCreatedTs()).isEqualTo(100L);
        assertThat(dl.getFailedTs()).isEqualTo(9000L);
        assertThat(dl.getRetries()).isEqualTo(5);
        assertThat(dl.getError()).isEqualTo("boom");
    }

    @Test
    void testFromTimeout() {
        final ProcessManagerTimeout timeout = new ProcessManagerTimeout("p-1", "OrderProcess", 2, 5000L, "await-ack", 100L);
        timeout.recordFailure("boom");

        final ProcessManagerTimeoutDeadLetter dl = ProcessManagerTimeoutDeadLetter.fromTimeout(timeout, 9000L);

        assertThat(dl.getProcessId()).isEqualTo("p-1");
        assertThat(dl.getProcessType()).isEqualTo("OrderProcess");
        assertThat(dl.getProcessVersion()).isEqualTo(2);
        assertThat(dl.getDeadlineTs()).isEqualTo(5000L);
        assertThat(dl.getPayload()).isEqualTo("await-ack");
        assertThat(dl.getCreatedTs()).isEqualTo(100L);
        assertThat(dl.getFailedTs()).isEqualTo(9000L);
        assertThat(dl.getRetries()).isEqualTo(1);
        assertThat(dl.getError()).isEqualTo("boom");
    }

}
