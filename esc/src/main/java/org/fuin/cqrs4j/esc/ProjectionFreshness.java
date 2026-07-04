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
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Computes how fresh a projection (read model) is: its current checkpoint {@code position} (the "as of N" value),
 * the {@code lag} (number of events still to process, from {@link ProjectionLag}), and whether it is
 * {@code caughtUp} (lag 0). Reading only the {@code position} is a cheap checkpoint lookup; computing the lag
 * reads the event store forward and is therefore better suited to a dedicated freshness query than to decorating
 * every read.
 */
@ThreadSafe
public final class ProjectionFreshness {

    private ProjectionFreshness() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of a utility class");
    }

    /**
     * Computes the freshness of a projection stream.
     *
     * @param eventStore         Event store the projection stream lives in.
     * @param projectionService  Service holding the projection checkpoint.
     * @param projectionStreamId Identifier of the projection stream.
     * @param chunkSize          Number of events read per round trip (must be &gt;= 1).
     * @return Freshness (position, lag, caught-up).
     */
    public static Freshness of(final EventStore eventStore, final ProjectionService projectionService,
                               final StreamId projectionStreamId, final int chunkSize) {
        Contract.requireArgNotNull("eventStore", eventStore);
        Contract.requireArgNotNull("projectionService", projectionService);
        Contract.requireArgNotNull("projectionStreamId", projectionStreamId);
        Contract.requireArgMin("chunkSize", chunkSize, 1);
        final long position = projectionService.readProjectionPosition(projectionStreamId);
        final long lag = ProjectionLag.unprocessedEventCount(eventStore, projectionService, projectionStreamId, chunkSize);
        return new Freshness(position, lag, lag == 0);
    }

    /**
     * Freshness of a projection.
     *
     * @param position The current checkpoint position (number of the next event to read).
     * @param lag      Number of events the projection still has to process.
     * @param caughtUp Whether the projection has processed every event in its stream (lag 0).
     */
    public record Freshness(long position, long lag, boolean caughtUp) {
    }

}
