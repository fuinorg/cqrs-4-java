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

import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Computes the projection lag - the number of events a projection still has to process - from the difference
 * between the head of the projection stream and the stored checkpoint. It reuses the exact forward-read the
 * view managers perform, so the value is exact. The read is cheap while the projection is caught up (the
 * first forward read from the checkpoint immediately hits the end of the stream) and only becomes heavier
 * while a projection is genuinely lagging.
 */
@ThreadSafe
public final class ProjectionLag {

    private ProjectionLag() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of a utility class");
    }

    /**
     * Returns the number of events between the projection's stored checkpoint and the head of its stream.
     *
     * @param eventStore        Event store the projection stream lives in.
     * @param projectionService Service holding the projection checkpoint.
     * @param projectionStreamId Identifier of the projection stream.
     * @param chunkSize         Number of events read per round trip (must be &gt;= 1).
     * @return Number of unprocessed events (0 if the stream does not exist yet or the projection is caught up).
     */
    public static long unprocessedEventCount(final EventStore eventStore, final ProjectionService projectionService,
                                             final StreamId projectionStreamId, final int chunkSize) {
        Contract.requireArgNotNull("eventStore", eventStore);
        Contract.requireArgNotNull("projectionService", projectionService);
        Contract.requireArgNotNull("projectionStreamId", projectionStreamId);
        Contract.requireArgMin("chunkSize", chunkSize, 1);

        if (!eventStore.streamExists(projectionStreamId)) {
            // No events have been projected into this stream yet
            return 0;
        }

        long position = projectionService.readProjectionPosition(projectionStreamId);
        long count = 0;
        StreamEventsSlice slice;
        do {
            slice = eventStore.readEventsForward(projectionStreamId, position, chunkSize);
            count += slice.getEvents().size();
            position = slice.getNextEventNumber();
        } while (!slice.isEndOfStream());
        return count;
    }

}
