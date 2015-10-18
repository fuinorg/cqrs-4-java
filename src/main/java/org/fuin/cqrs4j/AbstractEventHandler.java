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

import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ContractViolationException;

/**
 * Provides base functionality for event handlers.
 * 
 * @param <TYPE>
 *            Event type.
 */
public abstract class AbstractEventHandler<TYPE extends Event> implements EventHandler {

    private final EventType eventType;

    private final Class<?> eventClass;

    /**
     * Constructor with type and class.
     * 
     * @param eventType
     *            Event type.
     * @param eventClass
     *            Event class.
     */
    public AbstractEventHandler(@NotNull final EventType eventType, @NotNull final Class<?> eventClass) {
        Contract.requireArgNotNull("eventType", eventType);
        Contract.requireArgNotNull("eventClass", eventClass);
        this.eventType = eventType;
        this.eventClass = eventClass;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void handle(final Event event) {
        Contract.requireArgNotNull("event", event);
        if (!eventType.equals(event.getEventType())) {
            throw new ContractViolationException("The event has the wrong type: " + event.getEventType());
        }
        if (!eventClass.isAssignableFrom(event.getClass())) {
            throw new ContractViolationException("The event has wrong class: " + event.getClass().getName());
        }
        handleEvent((TYPE) event);
    }

    /**
     * Handles the event.
     * 
     * @param event
     *            Event to handle.
     */
    protected abstract void handleEvent(final TYPE event);

}
