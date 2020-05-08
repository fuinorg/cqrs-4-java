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

import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventId;

/**
 * Matches an event to a process instance.
 */
public interface ProcessEventMatcher {

    /**
     * Returns the unique type identifier of the matcher.
     * 
     * @return Identifier for this type.
     */
    @NotNull
    public ProcessEventMatcherType getType();

    /**
     * Data that can be used to reconstruct the instance.
     * 
     * @return Current state.
     */
    @NotNull
    public Object getState();

    /**
     * Returns a list of identifiers for event the matcher is waiting for.
     * 
     * @return List with unique event identifiers.
     */
    @NotEmpty
    public List<EventId> getEventIds();

    /**
     * Determines if the event is the expected one.
     * 
     * @param event
     *            Event to test.
     * 
     * @return {@literal true} if the process manager wants to handle this event, else {@literal false}.
     */
    public boolean matches(@NotNull Event event);

}
