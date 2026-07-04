package org.fuin.cqrs4j.springboot.pm.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link ProcessTimeoutMetrics} class.
 */
public class ProcessTimeoutMetricsTest {

    @Test
    public void testGaugesReflectCounts() {

        // PREPARE
        final ProcessTimeoutRepository repository = mock(ProcessTimeoutRepository.class);
        when(repository.pendingCount()).thenReturn(4L);
        when(repository.overdueCount(anyLong())).thenReturn(2L);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // TEST
        new ProcessTimeoutMetrics(repository).bindTo(registry);

        // VERIFY
        assertThat(registry.get(ProcessTimeoutMetrics.PENDING).gauge().value()).isEqualTo(4.0);
        assertThat(registry.get(ProcessTimeoutMetrics.OVERDUE).gauge().value()).isEqualTo(2.0);

    }

    @Test
    public void testGaugesTrackChanges() {

        // PREPARE
        final ProcessTimeoutRepository repository = mock(ProcessTimeoutRepository.class);
        when(repository.pendingCount()).thenReturn(0L, 5L);
        when(repository.overdueCount(anyLong())).thenReturn(0L);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ProcessTimeoutMetrics(repository).bindTo(registry);

        // TEST & VERIFY: the gauge re-reads the source on each measurement
        assertThat(registry.get(ProcessTimeoutMetrics.PENDING).gauge().value()).isEqualTo(0.0);
        assertThat(registry.get(ProcessTimeoutMetrics.PENDING).gauge().value()).isEqualTo(5.0);

    }

}
