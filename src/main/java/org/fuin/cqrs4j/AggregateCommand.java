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

import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.ddd4j.ddd.AggregateVersion;
import org.fuin.ddd4j.ddd.DomainEvent;

/**
 * Common behavior shared by all commands related to an aggregate.
 * 
 * @param <ID>
 *            Type of the aggregate root identifier.
 */
public interface AggregateCommand<ID extends AggregateRootId> extends Command, DomainEvent<ID> {

    /**
     * Returns the identifier of the aggregate root this command targets.
     * 
     * @return Aggregate root identifier.
     */
    @NotNull
    public ID getAggregateRootId();

    /**
     * Returns the aggregate version.
     * 
     * @return Expected version.
     */
    @Nullable
    public AggregateVersion getAggregateVersion();

    /**
     * Returns the aggregate version as integer. This is a null safe shortcut for <code>getAggregateVersion().asBaseType()</code>-
     * 
     * @return Expected version or {@literal null}.
     */
    @Nullable
    public Integer getAggregateVersionInteger();

}
