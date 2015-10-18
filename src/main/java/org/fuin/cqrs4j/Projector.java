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

import java.util.Set;

import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.NotEmpty;

/**
 * Reads events and updates it's view.
 */
public interface Projector {

    /**
     * Returns a unique identifier for the view.
     * 
     * @return Unique ID.
     */
    @NotEmpty
    public String getViewId();

    /**
     * Returns a human readable name for the view.
     * 
     * @return Unique name.
     */
    @NotEmpty
    public String getViewName();

    /**
     * Returns a brief description of what the view is good for.
     * 
     * @return Description.
     */
    @NotEmpty
    public String getViewDescription();

    /**
     * Drops and recreates the view content.
     */
    public void rebuildView();

    /**
     * Returns the event types this projector is interested in.
     * 
     * @return Immutable set of events the projector wants to handle.
     */
    @NotNull
    public Set<EventType> getEventTypes();

    /**
     * Handles the given event. Events the projector is not interested in are ignored.
     * 
     * @param event
     *            Event to apply to the view.
     */
    public void handle(@NotNull Event event);

}
