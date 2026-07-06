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

import jakarta.persistence.EntityManager;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

/**
 * Event handler that maps an event to JPA entities. A handler declares which events it operates on either by
 * exact event type ({@link #getEventType()}) or by category - a marker interface the events implement
 * ({@link #getEventCategory()}); a category handler receives every event implementing that interface.
 * All implementations are expected to be thread safe.
 *
 * @param <TYPE> Event type.
 */
@ThreadSafe
public interface EventHandler<TYPE extends Event> {

    /**
     * Returns the exact type of event this handler operates on, or {@literal null} if the handler selects by
     * category instead (see {@link #getEventCategory()}).
     *
     * @return Unique event type, or {@literal null} for a category handler.
     */
    @Nullable
    default EventType getEventType() {
        return null;
    }

    /**
     * Returns the event category (a marker interface the events implement) this handler operates on, or
     * {@literal null} if the handler selects by exact type instead (see {@link #getEventType()}). When set,
     * the handler is invoked for every event that is an instance of the returned interface.
     *
     * @return Marker interface class, or {@literal null} for an exact-type handler.
     */
    @Nullable
    default Class<?> getEventCategory() {
        return null;
    }

    /**
     * Modifies the view using the given event.
     *
     * @param entityManager Entity manager to use.
     * @param event         Event to use.
     */
    public void handle(EntityManager entityManager, TYPE event);

}
