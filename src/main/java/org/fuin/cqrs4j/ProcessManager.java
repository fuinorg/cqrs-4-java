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

import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.ddd4j.ddd.AggregateVersion;
import org.fuin.ddd4j.ddd.Event;

/**
 * Organizes and executes a business process.
 * 
 * @param <ID>
 *            Type of the process manager identifier.
 */
public interface ProcessManager<ID extends AggregateRootId> {

    /**
     * Returns a list of events that will start a process.
     * 
     * @return Events that will lead to a new running process manager instance.
     */
    @NotEmpty
    public List<Event> getStartEvents();

    /**
     * Processes the given event.
     * 
     * @param event
     *            Event to handle.
     */
    public ProcessResult process(@NotNull Event event);

    /**
     * Returns the unique process manager root identifier.
     * 
     * @return Identifier.
     */
    public ID getId();

    /**
     * Returns a list of uncommitted changes.
     * 
     * @return List of events that were not persisted yet.
     */
    @NotNull
    public List<Event> getUncommittedChanges();

    /**
     * Returns the information if the process manager has uncommited changes.
     * 
     * @return TRUE if the process manager will return a non-empty list for {@link #getUncommittedChanges()}, else FALSE.
     */
    public boolean hasUncommitedChanges();

    /**
     * Clears the internal change list and sets the new version number.
     */
    public void markChangesAsCommitted();

    /**
     * Returns the current version of the process manager.
     * 
     * @return Current version that does NOT included uncommitted changes.
     */
    public int getVersion();

    /**
     * Returns the next version of the process manager.
     * 
     * @return Next version that includes all currently uncommitted changes.
     */
    public int getNextVersion();

    /**
     * Returns the next version useful when creating an event for being applied:<br>
     * <code>apply(new MyEvent( ... , getNextApplyVersion()))</code>.
     * 
     * @return Version for the event.
     */
    public AggregateVersion getNextApplyVersion();

    /**
     * Loads the process manager with historic events.
     * 
     * @param history
     *            List of historic events.
     */
    public void loadFromHistory(@NotNull Event... history);

    /**
     * Loads the process manager with historic events.
     * 
     * @param history
     *            List of historic events.
     */
    public void loadFromHistory(@NotNull List<Event> history);

}
