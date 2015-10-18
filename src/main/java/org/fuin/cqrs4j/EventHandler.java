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

import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventType;

/**
 * Does something useful using the input from an event.
 */
public interface EventHandler {

    /**
     * Returns the type of event this handler operates on.
     * 
     * @return Unique event type.
     */
    public EventType getEventType();

    /**
     * Modifies the view using the given event.
     * 
     * @param event
     *            Event to use.
     */
    public void handle(Event event);

}
