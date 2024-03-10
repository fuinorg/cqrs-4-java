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

import jakarta.validation.constraints.NotNull;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.DomainEvent;
import org.fuin.ddd4j.core.EntityId;

/**
 * Common behavior shared by all commands related to an aggregate.
 *
 * @param <ROOT_ID>
 *            Type of the aggregate root identifier.
 * @param <ENTITY_ID>
 *            Type of the identifier (the last one in the path).
 */
public interface AggregateCommand<ROOT_ID extends AggregateRootId, ENTITY_ID extends EntityId> extends Command, DomainEvent<ENTITY_ID> {

    /**
     * Returns the identifier of the aggregate root this command targets.
     *
     * @return Aggregate root identifier.
     */
    @NotNull
    public ROOT_ID getAggregateRootId();

}
