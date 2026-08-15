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

import jakarta.validation.constraints.NotNull;
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps projection positions in memory, so a projection whose result also lives in memory needs no database.
 * <p>
 * The consequence is deliberate: nothing survives a restart, so every start replays the stream from the
 * beginning. For a projection whose state is a small in-memory structure that is the correct trade - it
 * removes the possibility of a checkpoint that has advanced past state that was never rebuilt, which is the
 * one way an in-memory projection can be quietly wrong. Within a single run the position still does its job
 * and stops each poll from re-reading everything.
 * <p>
 * Not suitable for a projection that writes to a database: there the checkpoint has to be committed with the
 * data it describes, which is what {@code QryProjectionService} does.
 * <p>
 * <b>Positions are not tenant-scoped.</b> The JPA implementation gets that for free, because its rows live in
 * the tenant's own schema; here there is one map. Reading several tenants' streams through a single instance
 * would therefore have them share a checkpoint and each skip the others' events. Use one instance per tenant
 * when that time comes - {@link AuthorizationProjectionRunner} refuses to run multi-tenant until it does.
 */
@ThreadSafe
public final class InMemoryProjectionService implements ProjectionService {

    private final Map<StreamId, Long> positions = new ConcurrentHashMap<>();

    @Override
    public void resetProjectionPosition(@NotNull final StreamId streamId) {
        Objects.requireNonNull(streamId, "streamId==null");
        positions.remove(streamId);
    }

    @Override
    @NotNull
    public Long readProjectionPosition(@NotNull final StreamId streamId) {
        Objects.requireNonNull(streamId, "streamId==null");
        return positions.getOrDefault(streamId, 0L);
    }

    @Override
    public void updateProjectionPosition(@NotNull final StreamId streamId, @NotNull final Long nextEventNumber) {
        Objects.requireNonNull(streamId, "streamId==null");
        Objects.requireNonNull(nextEventNumber, "nextEventNumber==null");
        positions.put(streamId, nextEventNumber);
    }

}
