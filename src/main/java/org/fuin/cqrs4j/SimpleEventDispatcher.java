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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.objects4j.common.Contract;

/**
 * Registry with all event handlers.
 */
public final class SimpleEventDispatcher implements EventDispatcher {

    @SuppressWarnings("rawtypes")
    private final Map<EventType, List<EventHandler>> eventHandlers;

    /**
     * Constructor with array of event handlers.
     * 
     * @param eventHandlers
     *            Event handlers.
     */
    @SuppressWarnings("rawtypes")
    public SimpleEventDispatcher(@NotNull final EventHandler... eventHandlers) {
        this(Arrays.asList(eventHandlers));
    }

    /**
     * Constructor with list of event handlers.
     * 
     * @param eventHandlers
     *            Event handlers.
     */
    @SuppressWarnings("rawtypes")
    public SimpleEventDispatcher(@NotNull final List<EventHandler> eventHandlers) {
        super();
        Contract.requireArgNotNull("eventHandlers", eventHandlers);
        if (eventHandlers.isEmpty()) {
            throw new IllegalArgumentException("The argument 'eventHandlers' cannot be an empty list");
        }
        this.eventHandlers = new HashMap<>();
        for (final EventHandler eventHandler : eventHandlers) {
            List<EventHandler> handlers = this.eventHandlers.get(eventHandler.getEventType());
            if (handlers == null) {
                handlers = new ArrayList<>();
                this.eventHandlers.put(eventHandler.getEventType(), handlers);
            }
            handlers.add(eventHandler);
        }
    }

    @Override
    @NotNull
    public final Set<EventType> getAllTypes() {
        return eventHandlers.keySet();
    }

    @Override
    public final void dispatchCommonEvents(@NotNull final List<CommonEvent> commonEvents) {

        Contract.requireArgNotNull("commonEvents", commonEvents);

        for (final CommonEvent commonEvent : commonEvents) {
            final Event event = (Event) commonEvent.getData();
            dispatchEvent(event);
        }
    }

    @Override
    public final void dispatchEvents(@NotNull final List<Event> events) {

        Contract.requireArgNotNull("events", events);

        for (final Event event : events) {
            dispatchEvent(event);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public final void dispatchEvent(@NotNull final Event event) {

        Contract.requireArgNotNull("event", event);

        final List<EventHandler> handlers = eventHandlers.get(event.getEventType());
        if (handlers != null) {
            for (final EventHandler handler : handlers) {
                handler.handle(event);
            }
        }
    }

}
