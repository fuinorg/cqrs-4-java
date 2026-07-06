package org.fuin.cqrs4j.springboot.query.core.view;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.StreamEventsSlice;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link ProjectionLagMetrics} class.
 */
public class ProjectionLagMetricsTest {

    @Test
    public void testLagGaugePerView() {

        // PREPARE
        final ViewRegistry.Entry entry = new ViewRegistry.Entry(View.class, "MyView", "myViewBean",
                "MyProjection", "MyStream", "*/5 * * * * *", 100, Set.of(new EventType("MyEvent")), Set.of());
        final ViewRegistry viewRegistry = mock(ViewRegistry.class);
        when(viewRegistry.getViews()).thenReturn(List.of(entry));

        final EventStore eventstore = mock(EventStore.class);
        final ProjectionService projectionService = mock(ProjectionService.class);
        when(eventstore.streamExists(any())).thenReturn(true);
        when(projectionService.readProjectionPosition(any())).thenReturn(2L);
        // 4 events remain between checkpoint (2) and head (6)
        when(eventstore.readEventsForward(any(), eq(2L), eq(100)))
                .thenReturn(new StreamEventsSlice(2, Collections.nCopies(4, mock(CommonEvent.class)), 6, true));

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // TEST
        new ProjectionLagMetrics(viewRegistry, eventstore, projectionService, null, null).bindTo(registry);

        // VERIFY
        assertThat(registry.get(ProjectionLagMetrics.PROJECTION_LAG).tag("view", "MyView").gauge().value())
                .isEqualTo(4.0);

    }

    @Test
    public void testLagIsZeroWhenCaughtUp() {

        // PREPARE
        final ViewRegistry.Entry entry = new ViewRegistry.Entry(View.class, "MyView", "myViewBean",
                "MyProjection", "MyStream", "*/5 * * * * *", 100, Set.of(new EventType("MyEvent")), Set.of());
        final ViewRegistry viewRegistry = mock(ViewRegistry.class);
        when(viewRegistry.getViews()).thenReturn(List.of(entry));

        final EventStore eventstore = mock(EventStore.class);
        final ProjectionService projectionService = mock(ProjectionService.class);
        when(eventstore.streamExists(any())).thenReturn(true);
        when(projectionService.readProjectionPosition(any())).thenReturn(6L);
        when(eventstore.readEventsForward(any(), eq(6L), eq(100)))
                .thenReturn(new StreamEventsSlice(6, Collections.emptyList(), 6, true));

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // TEST
        new ProjectionLagMetrics(viewRegistry, eventstore, projectionService, null, null).bindTo(registry);

        // VERIFY
        assertThat(registry.get(ProjectionLagMetrics.PROJECTION_LAG).tag("view", "MyView").gauge().value())
                .isZero();

    }

}
