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
package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Set;

/**
 * One contributor to the authorization projection: a set of event types, and how they fold into
 * {@link PermissionState}.
 * <p>
 * The projection is an ordinary multi-source projection. An application typically starts with a single
 * source - the aggregate that records who holds what - but nothing here is bound to one aggregate. A second
 * aggregate that later grants permissions of its own registers another implementation of this interface and
 * the projection picks it up: {@link AuthorizationView} takes the union of every source's
 * {@link #eventTypes()} and offers each event to every source. Neither the view nor the runner changes.
 * <p>
 * The fold is a <b>union, and there is no deny</b>. Two sources granting the same permission collapse to one
 * entry, and two sources therefore cannot contradict each other.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface PermissionEventSource {

    /**
     * Returns the event types this source folds. They become part of the projection's selection, so an event
     * type not named here never reaches {@link #apply(TenantId, Event, PermissionState)}.
     *
     * @return Event types, never empty.
     */
    Set<EventType> eventTypes();

    /**
     * Folds one event into the state.
     * <p>
     * Called for every event the projection selects, which may include types another source registered - an
     * implementation must ignore anything it does not recognise rather than fail. Must be idempotent: the
     * projection replays from the beginning of the stream on every start.
     * <p>
     * The tenant is passed rather than read from an ambient context, and must be used as part of every key
     * written into the state. An event carries no tenant of its own - which tenant's stream it came from is
     * known only to the caller - so a source that ignored this parameter would silently merge tenants once
     * more than one is read.
     *
     * @param tenantId Tenant whose stream this event came from.
     * @param event    Event to fold.
     * @param state    State to update.
     */
    void apply(TenantId tenantId, Event event, PermissionState state);

}
