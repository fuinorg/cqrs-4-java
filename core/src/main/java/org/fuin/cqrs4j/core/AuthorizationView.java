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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The view that answers "who may do what", folded out of the event streams into memory.
 * <p>
 * An ordinary {@link View}, deliberately: the command side now hosts a projection, and it should be
 * recognisably the same construct the query side has always used rather than a mechanism of its own. The
 * only difference is where it puts what it reads - a {@link PermissionState} in memory instead of a database
 * table - which is why it needs no persistence and can run in a deployable that has no JPA at all.
 * <p>
 * It is a <b>multi-source</b> projection. Its event types are the union over the registered
 * {@link PermissionEventSource}s, and every selected event is offered to each of them. An application that
 * later grows a second aggregate granting permissions registers one more source; this class does not change.
 * <p>
 * There is no stored checkpoint, so every start replays the stream from the beginning. That is affordable
 * because the state is small, and it is what makes a retired event type a non-event: the fold simply stops
 * recognising it.
 */
@ThreadSafe
public final class AuthorizationView implements View {

    /** Name used for the view, its bean and its projection. Fixed, so the id cannot drift per deployment. */
    public static final String NAME = "Authorization";

    private final List<PermissionEventSource> sources;

    private final PermissionState state;

    private final Set<EventType> eventTypes;

    private final String cron;

    private final TenantId defaultTenantId;

    /**
     * Constructor with the registered sources and the state they fold into.
     *
     * @param sources         Contributors to the projection. Must not be empty - a projection with no source
     *                        would select no events and silently answer "holds nothing" for everybody.
     * @param state           State the sources fold into.
     * @param cron            CRON expression, for runtimes that schedule views that way.
     * @param defaultTenantId Tenant used by {@link #handleEvents(List)}, the tenant-less {@link View} method.
     */
    public AuthorizationView(final List<PermissionEventSource> sources, final PermissionState state,
                             final String cron, final TenantId defaultTenantId) {
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources==null"));
        this.state = Objects.requireNonNull(state, "state==null");
        this.cron = Objects.requireNonNull(cron, "cron==null");
        this.defaultTenantId = Objects.requireNonNull(defaultTenantId, "defaultTenantId==null");
        if (this.sources.isEmpty()) {
            throw new IllegalArgumentException("The argument 'sources' cannot be an empty list - a projection "
                    + "with no source would deny every request without saying why");
        }
        final Set<EventType> types = new LinkedHashSet<>();
        for (final PermissionEventSource source : this.sources) {
            types.addAll(source.eventTypes());
        }
        if (types.isEmpty()) {
            throw new IllegalArgumentException("None of the " + this.sources.size()
                    + " permission event source(s) declares an event type");
        }
        this.eventTypes = Set.copyOf(types);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<? extends View> getBeanClass() {
        return AuthorizationView.class;
    }

    @Override
    public String getBeanName() {
        return "authorizationView";
    }

    @Override
    public Set<EventType> getEventTypes() {
        return eventTypes;
    }

    @Override
    public String getCron() {
        return cron;
    }

    @Override
    public void handleEvents(final List<Event> events) {
        handleEvents(defaultTenantId, events);
    }

    /**
     * Folds a chunk of events read from one tenant's stream.
     * <p>
     * The tenant is explicit rather than taken from an ambient thread-local. An events chunk carries no
     * tenant of its own, and a projection that guessed wrong would attribute one tenant's grants to another
     * - so the caller, which knows whose stream it just read, has to say.
     *
     * @param tenantId Tenant whose stream the events came from.
     * @param events   Events to fold.
     */
    public void handleEvents(final TenantId tenantId, final List<Event> events) {
        Objects.requireNonNull(tenantId, "tenantId==null");
        Objects.requireNonNull(events, "events==null");
        for (final Event event : events) {
            for (final PermissionEventSource source : sources) {
                // Every source sees every selected event, including types another source registered, and
                // ignores what it does not recognise. That keeps sources independent of each other.
                source.apply(tenantId, event, state);
            }
        }
    }

    /**
     * Returns the state this view maintains.
     *
     * @return State, never {@literal null}.
     */
    public PermissionState getState() {
        return state;
    }

}
