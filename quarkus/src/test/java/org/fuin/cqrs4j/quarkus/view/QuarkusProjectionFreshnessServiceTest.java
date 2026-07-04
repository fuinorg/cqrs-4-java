package org.fuin.cqrs4j.quarkus.view;

import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionFreshness.Freshness;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.esc.ProjectionStreamIds;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.StreamEventsSlice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link QuarkusProjectionFreshnessService} class with mocked collaborators.
 */
public class QuarkusProjectionFreshnessServiceTest {

    private static final String VIEW = "PersonsView";

    private EventStore eventstore;

    private ProjectionService projectionService;

    private ProjectionStreamId streamId;

    private QuarkusProjectionFreshnessService testee;

    @BeforeEach
    public void setUp() {
        final ViewRegistry viewRegistry = mock(ViewRegistry.class);
        eventstore = mock(EventStore.class);
        projectionService = mock(ProjectionService.class);
        final ViewRegistry.Entry entry = new ViewRegistry.Entry(View.class, VIEW, "PersonsViewBean",
                "PersonsProjection", "PersonsStream", "* * * * * *", 100,
                Set.of(new EventType("PersonCreatedEvent")));
        streamId = ProjectionStreamIds.of(entry);
        when(viewRegistry.getViews()).thenReturn(List.of(entry));
        testee = new QuarkusProjectionFreshnessService();
        testee.viewRegistry = viewRegistry;
        testee.eventstore = eventstore;
        testee.projectionService = projectionService;
    }

    @Test
    public void testPosition() {
        // PREPARE
        when(projectionService.readProjectionPosition(streamId)).thenReturn(7L);

        // TEST & VERIFY
        assertThat(testee.position(VIEW)).isEqualTo(7);
    }

    @Test
    public void testFreshnessCaughtUp() {
        // PREPARE: checkpoint at head
        when(projectionService.readProjectionPosition(streamId)).thenReturn(7L);
        when(eventstore.streamExists(streamId)).thenReturn(true);
        when(eventstore.readEventsForward(streamId, 7, 100)).thenReturn(slice(7, 0, 7, true));

        // TEST
        final Freshness freshness = testee.freshness(VIEW);

        // VERIFY
        assertThat(freshness.position()).isEqualTo(7);
        assertThat(freshness.lag()).isZero();
        assertThat(freshness.caughtUp()).isTrue();
    }

    @Test
    public void testFreshnessLagging() {
        // PREPARE: 4 events behind
        when(projectionService.readProjectionPosition(streamId)).thenReturn(3L);
        when(eventstore.streamExists(streamId)).thenReturn(true);
        when(eventstore.readEventsForward(streamId, 3, 100)).thenReturn(slice(3, 4, 7, true));

        // TEST
        final Freshness freshness = testee.freshness(VIEW);

        // VERIFY
        assertThat(freshness.position()).isEqualTo(3);
        assertThat(freshness.lag()).isEqualTo(4);
        assertThat(freshness.caughtUp()).isFalse();
    }

    @Test
    public void testUnknownViewThrows() {
        assertThatThrownBy(() -> testee.position("Nope")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> testee.freshness("Nope")).isInstanceOf(IllegalArgumentException.class);
    }

    private static StreamEventsSlice slice(final long from, final int eventCount, final long next,
                                           final boolean endOfStream) {
        final List<CommonEvent> events = Collections.nCopies(eventCount, mock(CommonEvent.class));
        return new StreamEventsSlice(from, events, next, endOfStream);
    }

}
