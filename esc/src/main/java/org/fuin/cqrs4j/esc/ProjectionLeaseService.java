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

/**
 * Distributed, time-limited lease that makes projection processing multi-instance safe: only the owner that
 * currently holds the lease for a projection stream may fold events into the read model, so competing
 * application instances do not double-write. The lease is keyed by {@link StreamId#asString()} (tenant
 * isolation is provided by the routing datasource, exactly like the projection checkpoint) and expires after a
 * time-to-live so a crashed owner does not block progress forever.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface ProjectionLeaseService {

    /**
     * Tries to acquire (or renew) the lease for the given stream.
     *
     * @param streamId  Unique id of the projection stream.
     * @param owner     Identifier of the calling application instance.
     * @param ttlMillis Time-to-live in milliseconds for which the lease is granted.
     * @return {@literal true} if the {@code owner} holds the lease after this call (freshly acquired, or a
     * renewal of its own or an expired lease); {@literal false} if a different, non-expired owner holds it.
     */
    boolean acquire(@NotNull StreamId streamId, @NotNull String owner, long ttlMillis);

    /**
     * Extends the lease expiry if it is still held by the given owner (best effort - a no-op if the lease was
     * lost in the meantime). Used to keep a lease alive during a long catch-up.
     *
     * @param streamId  Unique id of the projection stream.
     * @param owner     Identifier of the calling application instance.
     * @param ttlMillis Time-to-live in milliseconds to extend the lease by.
     */
    void renew(@NotNull StreamId streamId, @NotNull String owner, long ttlMillis);

    /**
     * Releases the lease if it is held by the given owner (a no-op otherwise).
     *
     * @param streamId Unique id of the projection stream.
     * @param owner    Identifier of the calling application instance.
     */
    void release(@NotNull StreamId streamId, @NotNull String owner);

}
