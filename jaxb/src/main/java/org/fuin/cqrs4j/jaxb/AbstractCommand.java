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
package org.fuin.cqrs4j.jaxb;

import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.core.Command;
import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.jaxb.AbstractEvent;
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
     * Constructor with event this one responds to. Convenience method to set the correlation and causation identifiers correctly.
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

    /**
     * Base class for event builders.
     *
     * @param <ID>
     *            Type of the entity identifier.
     * @param <TYPE>
     *            Type of the event.
     * @param <BUILDER>
     *            Type of the builder.
     */
    protected abstract static class Builder<ID extends EntityId, TYPE extends AbstractCommand, BUILDER extends Builder<ID, TYPE, BUILDER>>
            extends AbstractEvent.Builder<TYPE, BUILDER> {

        private AbstractCommand delegate;

        /**
         * Constructor with event.
         *
         * @param delegate
         *            Event to populate with data.
         */
        public Builder(final TYPE delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        /**
         * Ensures that everything is setup for building the object or throws a runtime exception otherwise.
         */
        protected final void ensureBuildableAbstractCommand() {
            ensureBuildableAbstractEvent();
        }

        /**
         * Sets the internal instance to a new one. This must be called within the build method.
         *
         * @param delegate
         *            Delegate to use.
         */
        protected final void resetAbstractCommand(final TYPE delegate) {
            resetAbstractEvent(delegate);
            this.delegate = delegate;
        }

    }

}
