package org.fuin.cqrs4j.springboot.pm.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link OutboxMetrics} class.
 */
public class OutboxMetricsTest {

    @Test
    public void testGaugesReflectCounts() {

        // PREPARE
        final CommandOutboxService outboxService = mock(CommandOutboxService.class);
        when(outboxService.outboxDepth()).thenReturn(3L);
        when(outboxService.deadLetterCount()).thenReturn(1L);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // TEST
        new OutboxMetrics(outboxService).bindTo(registry);

        // VERIFY
        assertThat(registry.get(OutboxMetrics.OUTBOX_DEPTH).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get(OutboxMetrics.DEAD_LETTER_COUNT).gauge().value()).isEqualTo(1.0);

    }

    @Test
    public void testGaugesTrackChanges() {

        // PREPARE
        final CommandOutboxService outboxService = mock(CommandOutboxService.class);
        when(outboxService.outboxDepth()).thenReturn(0L, 5L);
        when(outboxService.deadLetterCount()).thenReturn(0L);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new OutboxMetrics(outboxService).bindTo(registry);

        // TEST & VERIFY: the gauge re-reads the source on each measurement
        assertThat(registry.get(OutboxMetrics.OUTBOX_DEPTH).gauge().value()).isEqualTo(0.0);
        assertThat(registry.get(OutboxMetrics.OUTBOX_DEPTH).gauge().value()).isEqualTo(5.0);

    }

}
