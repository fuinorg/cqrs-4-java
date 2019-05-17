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

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.AbstractEvent;
import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventId;

/**
 * Base class for all commands.
 * 
 * @param <ID>
 *            Type of the entity identifier.
 */
public abstract class AbstractDomainCommand<ID extends AggregateRootId> extends AbstractEvent implements DomainCommand<ID> {

    private static final long serialVersionUID = 1000L;

    /**
     * Default constructor.
     */
    public AbstractDomainCommand() {
        super();
    }

    /**
     * Constructor with event the command responds to. Convenience method to set the correlation and causation identifiers correctly.
     * 
     * @param respondTo
     *            Causing event.
     */
    public AbstractDomainCommand(@NotNull final Event respondTo) {
        super(respondTo);
    }

    /**
     * Constructor with correlation and causation identifiers.
     * 
     * @param correlationId
     *            Correlation ID.
     * @param causationId
     *            ID of the event that caused this one.
     */
    public AbstractDomainCommand(@Nullable final EventId correlationId, @Nullable final EventId causationId) {
        super(correlationId, causationId);
    }

}
