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
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.CommonEvent;

import java.util.List;
import java.util.Set;

/**
 * Registry with all JPA event handlers.
 */
public interface JpaEventDispatcher {

    /**
     * Returns a set of all known types.
     *
     * @return All known event types.
     */
    @NotNull
    public Set<EventType> getAllTypes();

    /**
     * Dispatch all common events to the appropriate event handler.
     *
     * @param entityManager Entity manager to use.
     * @param commonEvents  Events to dispatch.
     */
    public void dispatchCommonEvents(@NotNull EntityManager entityManager, @NotNull List<CommonEvent> commonEvents);

    /**
     * Dispatch all events to the appropriate event handler.
     *
     * @param entityManager Entity manager to use.
     * @param events        Events to dispatch.
     */
    public void dispatchEvents(@NotNull EntityManager entityManager, @NotNull List<Event> events);

    /**
     * Dispatches the given event to the appropriate event handler. The event is ignored if no event handler can be found that is capable of
     * handling it.
     *
     * @param entityManager Entity manager to use.
     * @param event         Event to dispatch.
     */
    public void dispatchEvent(@NotNull EntityManager entityManager, @NotNull Event event);

}
