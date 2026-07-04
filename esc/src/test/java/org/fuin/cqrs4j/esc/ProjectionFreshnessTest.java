/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.esc;

import org.fuin.cqrs4j.esc.ProjectionFreshness.Freshness;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.StreamId;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link ProjectionFreshness} class.
 */
public class ProjectionFreshnessTest {

    private static final StreamId STREAM_ID = new SimpleStreamId("MyView");

    private static final int CHUNK = 100;

    @Test
    public void testCaughtUp() {

        // PREPARE: checkpoint at head - the forward read returns no events and end-of-stream
        final EventStore eventStore = mock(EventStore.class);
        final ProjectionService projectionService = mock(ProjectionService.class);
        when(eventStore.streamExists(STREAM_ID)).thenReturn(true);
        when(projectionService.readProjectionPosition(STREAM_ID)).thenReturn(5L);
        when(eventStore.readEventsForward(STREAM_ID, 5, CHUNK)).thenReturn(slice(5, 0, 5, true));

        // TEST
        final Freshness freshness = ProjectionFreshness.of(eventStore, projectionService, STREAM_ID, CHUNK);

        // VERIFY
        assertThat(freshness.position()).isEqualTo(5);
        assertThat(freshness.lag()).isZero();
        assertThat(freshness.caughtUp()).isTrue();

    }

    @Test
    public void testLagging() {

        // PREPARE: 3 events behind
        final EventStore eventStore = mock(EventStore.class);
        final ProjectionService projectionService = mock(ProjectionService.class);
        when(eventStore.streamExists(STREAM_ID)).thenReturn(true);
        when(projectionService.readProjectionPosition(STREAM_ID)).thenReturn(2L);
        when(eventStore.readEventsForward(STREAM_ID, 2, CHUNK)).thenReturn(slice(2, 3, 5, true));

        // TEST
        final Freshness freshness = ProjectionFreshness.of(eventStore, projectionService, STREAM_ID, CHUNK);

        // VERIFY
        assertThat(freshness.position()).isEqualTo(2);
        assertThat(freshness.lag()).isEqualTo(3);
        assertThat(freshness.caughtUp()).isFalse();

    }

    @Test
    public void testStreamDoesNotExist() {

        // PREPARE: nothing projected yet
        final EventStore eventStore = mock(EventStore.class);
        final ProjectionService projectionService = mock(ProjectionService.class);
        when(eventStore.streamExists(STREAM_ID)).thenReturn(false);
        when(projectionService.readProjectionPosition(STREAM_ID)).thenReturn(0L);

        // TEST
        final Freshness freshness = ProjectionFreshness.of(eventStore, projectionService, STREAM_ID, CHUNK);

        // VERIFY: no lag, caught up
        assertThat(freshness.position()).isZero();
        assertThat(freshness.lag()).isZero();
        assertThat(freshness.caughtUp()).isTrue();

    }

    private static StreamEventsSlice slice(final long from, final int eventCount, final long next,
                                           final boolean endOfStream) {
        final List<CommonEvent> events = Collections.nCopies(eventCount, mock(CommonEvent.class));
        return new StreamEventsSlice(from, events, next, endOfStream);
    }

}
