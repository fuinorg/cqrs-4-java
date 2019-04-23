/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved. 
 * http://www.fuin.org/
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j;

import javax.validation.constraints.NotNull;

import org.fuin.esc.api.StreamId;

/**
 * Provides functionality related to projections.
 */
public interface ProjectionService {

    /**
     * Sets the stored position of the projection to the start position.
     * 
     * @param streamId
     *            Unique ID of the stream.
     * 
     */
    public void resetProjectionPosition(@NotNull final StreamId streamId);

    /**
     * Reads the position that was read last time.
     * 
     * @param streamId
     *            Unique ID of the stream.
     * 
     * @return Number of the next event to read.
     */
    @NotNull
    public Long readProjectionPosition(@NotNull StreamId streamId);

    /**
     * Updates the position to read next time.
     * 
     * @param streamId
     *            Unique ID of the stream.
     * @param nextEventNumber
     *            Number of the next event to read.
     */
    public void updateProjectionPosition(@NotNull StreamId streamId, @NotNull Long nextEventNumber);

}
