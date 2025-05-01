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

import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.core.JpaEventHandler;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.objects4j.common.Contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry with all JPA event handlers.
 */
public final class SimpleJpaEventDispatcher implements JpaEventDispatcher {

    @SuppressWarnings("rawtypes")
    private final Map<EventType, List<JpaEventHandler>> eventHandlers;

    /**
     * Constructor with array of event handlers.
     *
     * @param jpaEventHandlers
     *            Event handlers.
     */
    @SuppressWarnings("rawtypes")
    public SimpleJpaEventDispatcher(@NotNull final JpaEventHandler... jpaEventHandlers) {
        this(Arrays.asList(jpaEventHandlers));
    }

    /**
     * Constructor with list of event handlers.
     *
     * @param jpaEventHandlers
     *            Event handlers.
     */
    @SuppressWarnings("rawtypes")
    public SimpleJpaEventDispatcher(@NotNull final List<JpaEventHandler> jpaEventHandlers) {
        super();
        Contract.requireArgNotNull("eventHandlers", jpaEventHandlers);
        if (jpaEventHandlers.isEmpty()) {
            throw new IllegalArgumentException("The argument 'eventHandlers' cannot be an empty list");
        }
        this.eventHandlers = new HashMap<>();
        for (final JpaEventHandler jpaEventHandler : jpaEventHandlers) {
            List<JpaEventHandler> handlers = this.eventHandlers.get(jpaEventHandler.getEventType());
            if (handlers == null) {
                handlers = new ArrayList<>();
                this.eventHandlers.put(jpaEventHandler.getEventType(), handlers);
            }
            handlers.add(jpaEventHandler);
        }
    }

    @Override
    @NotNull
    public final Set<EventType> getAllTypes() {
        return eventHandlers.keySet();
    }

    @Override
    public final void dispatchCommonEvents(@NotNull EntityManager em, @NotNull final List<CommonEvent> commonEvents) {

        Contract.requireArgNotNull("commonEvents", commonEvents);

        for (final CommonEvent commonEvent : commonEvents) {
            final Event event = (Event) commonEvent.getData();
            dispatchEvent(em, event);
        }
    }

    @Override
    public final void dispatchEvents(@NotNull EntityManager em, @NotNull final List<Event> events) {

        Contract.requireArgNotNull("events", events);

        for (final Event event : events) {
            dispatchEvent(em, event);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public final void dispatchEvent(@NotNull EntityManager em, @NotNull final Event event) {

        Contract.requireArgNotNull("event", event);

        final List<JpaEventHandler> handlers = eventHandlers.get(event.getEventType());
        if (handlers != null) {
            for (final JpaEventHandler handler : handlers) {
                handler.handle(em, event);
            }
        }
    }

}
