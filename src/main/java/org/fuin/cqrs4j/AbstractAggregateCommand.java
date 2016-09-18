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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.fuin.ddd4j.ddd.AggregateVersion;
import org.fuin.ddd4j.ddd.AggregateVersionConverter;
import org.fuin.ddd4j.ddd.EntityIdPath;
import org.fuin.ddd4j.ddd.EntityIdPathConverter;
import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.Nullable;

/**
 * Base class for all commands that are directed to an existing aggregate.
 */
public abstract class AbstractAggregateCommand extends AbstractCommand {

    private static final long serialVersionUID = 1000L;

    @NotNull
    @XmlJavaTypeAdapter(EntityIdPathConverter.class)
    @XmlElement(name = "entity-id-path")
    private EntityIdPath entityIdPath;

    @NotNull
    @XmlJavaTypeAdapter(AggregateVersionConverter.class)
    @XmlElement(name = "aggregate-version")
    private AggregateVersion aggregateVersion;

    /**
     * Default constructor for JAXB.
     */
    protected AbstractAggregateCommand() {
        super();
    }

    /**
     * Constructor with entitiy id path and version.
     * 
     * @param entityIdPath
     *            Path from root aggregate to target entity.
     * @param aggregateVersion
     *            Expected aggregate version.
     */
    public AbstractAggregateCommand(@NotNull final EntityIdPath entityIdPath,
            @NotNull final AggregateVersion aggregateVersion) {
        super();
        Contract.requireArgNotNull("entityIdPath", entityIdPath);
        Contract.requireArgNotNull("aggregateVersion", aggregateVersion);
        this.entityIdPath = entityIdPath;
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * Constructor with event this one responds to. Convenience method to set
     * the correlation and causation identifiers correctly.
     * 
     * @param entityIdPath
     *            Path from root aggregate to target entity.
     * @param aggregateVersion
     *            Expected aggregate version.
     * @param respondTo
     *            Causing event.
     */
    public AbstractAggregateCommand(@NotNull final EntityIdPath entityIdPath,
            @NotNull final AggregateVersion aggregateVersion, @NotNull final Event respondTo) {
        super(respondTo);
        Contract.requireArgNotNull("entityIdPath", entityIdPath);
        Contract.requireArgNotNull("aggregateVersion", aggregateVersion);
        this.entityIdPath = entityIdPath;
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * Constructor with optional data.
     * 
     * @param entityIdPath
     *            Path from root aggregate to target entity.
     * @param aggregateVersion
     *            Expected aggregate version.
     * @param correlationId
     *            Correlation ID.
     * @param causationId
     *            ID of the event that caused this one.
     */
    public AbstractAggregateCommand(@NotNull final EntityIdPath entityIdPath,
            @NotNull final AggregateVersion aggregateVersion, @Nullable final EventId correlationId,
            @Nullable final EventId causationId) {
        super(correlationId, causationId);
        Contract.requireArgNotNull("entityIdPath", entityIdPath);
        Contract.requireArgNotNull("aggregateVersion", aggregateVersion);
        this.entityIdPath = entityIdPath;
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * Returns the entity identifier path.
     * 
     * @return Path from root aggregate to target entity.
     */
    @NotNull
    public final EntityIdPath getEntityIdPath() {
        return entityIdPath;
    }

    /**
     * Returns the aggregate version.
     * 
     * @return Expected version.
     */
    public final AggregateVersion getAggregateVersion() {
        return aggregateVersion;
    }

}
