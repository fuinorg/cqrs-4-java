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

import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Derives the {@link ProjectionStreamId} of a view's projection stream from its {@link ViewRegistry.Entry}. The
 * id is the view's stream name suffixed with an Adler-32 checksum of its event-type set, so it is stable for a
 * given set of event types and changes (invalidating the projection and its checkpoint) when the set changes.
 * This is the single mapping used both to drive a view and to look up its checkpoint / freshness.
 */
@ThreadSafe
public final class ProjectionStreamIds {

    private ProjectionStreamIds() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of a utility class");
    }

    /**
     * Returns the projection stream id for a view.
     *
     * @param entry View registry entry.
     * @return Projection stream id ({@code streamName-<checksum>}).
     */
    public static ProjectionStreamId of(final ViewRegistry.Entry entry) {
        Contract.requireArgNotNull("entry", entry);
        return new ProjectionStreamId(entry.streamName() + "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes()));
    }

}
