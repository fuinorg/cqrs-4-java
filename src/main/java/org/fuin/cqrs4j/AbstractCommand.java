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

import org.fuin.ddd4j.ddd.AbstractEvent;
import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventId;
import org.fuin.objects4j.common.Nullable;

/**
 * Base class for all commands.
 */
public abstract class AbstractCommand extends AbstractEvent implements Command {

    private static final long serialVersionUID = 1000L;

    /**
     * Default constructor.
     */
    public AbstractCommand() {
        super();
    }

    /**
     * Constructor with event this one responds to. Convenience method to set the correlation and causation
     * identifiers correctly.
     * 
     * @param respondTo
     *            Causing event.
     */
    public AbstractCommand(@NotNull final Event respondTo) {
        super(respondTo);
    }

    /**
     * Constructor with optional data.
     * 
     * @param correlationId
     *            Correlation ID.
     * @param causationId
     *            ID of the event that caused this one.
     */
    public AbstractCommand(@Nullable final EventId correlationId, @Nullable final EventId causationId) {
        super(correlationId, causationId);
    }

}
