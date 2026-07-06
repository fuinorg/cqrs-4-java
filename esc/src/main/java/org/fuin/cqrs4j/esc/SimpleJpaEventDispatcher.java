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
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry with all JPA event handlers.
 */
@ThreadSafe
public final class SimpleJpaEventDispatcher implements JpaEventDispatcher {

    @SuppressWarnings("rawtypes")
    private final Map<EventType, List<EventHandler>> eventHandlers;

    /** Handlers registered by category (marker interface the events implement). */
    @SuppressWarnings("rawtypes")
    private final Map<Class<?>, List<EventHandler>> categoryHandlers;

    /**
     * Constructor with array of event handlers.
     *
     * @param eventHandlers
     *            Event handlers.
     */
    @SuppressWarnings("rawtypes")
    public SimpleJpaEventDispatcher(@NotNull final EventHandler... eventHandlers) {
        this(Arrays.asList(eventHandlers));
    }

    /**
     * Constructor with list of event handlers.
     *
     * @param eventHandlers
     *            Event handlers.
     */
    @SuppressWarnings("rawtypes")
    public SimpleJpaEventDispatcher(@NotNull final List<EventHandler> eventHandlers) {
        super();
        Contract.requireArgNotNull("eventHandlers", eventHandlers);
        if (eventHandlers.isEmpty()) {
            throw new IllegalArgumentException("The argument 'eventHandlers' cannot be an empty list");
        }
        this.eventHandlers = new HashMap<>();
        this.categoryHandlers = new HashMap<>();
        for (final EventHandler eventHandler : eventHandlers) {
            final EventType eventType = eventHandler.getEventType();
            final Class<?> eventCategory = eventHandler.getEventCategory();
            if (eventType == null && eventCategory == null) {
                throw new IllegalArgumentException(
                        "Event handler declares neither an event type nor a category: " + eventHandler.getClass().getName());
            }
            if (eventType != null) {
                this.eventHandlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(eventHandler);
            }
            if (eventCategory != null) {
                this.categoryHandlers.computeIfAbsent(eventCategory, k -> new ArrayList<>()).add(eventHandler);
            }
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

        // Route to exact-type handlers and to category handlers whose marker interface the event implements.
        // A LinkedHashSet keeps a stable order (type handlers first) and invokes a handler registered under
        // both a type and a matching category only once.
        final Set<EventHandler> toInvoke = new LinkedHashSet<>();
        final List<EventHandler> typeMatched = eventHandlers.get(event.getEventType());
        if (typeMatched != null) {
            toInvoke.addAll(typeMatched);
        }
        for (final Map.Entry<Class<?>, List<EventHandler>> entry : categoryHandlers.entrySet()) {
            if (entry.getKey().isInstance(event)) {
                toInvoke.addAll(entry.getValue());
            }
        }
        for (final EventHandler handler : toInvoke) {
            handler.handle(em, event);
        }
    }

}
