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

import org.fuin.objects4j.common.Nullable;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbTypeAdapter;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.ddd4j.ddd.AggregateVersion;
import org.fuin.ddd4j.ddd.AggregateVersionConverter;
import org.fuin.ddd4j.ddd.EntityId;
import org.fuin.ddd4j.ddd.EntityIdPath;
import org.fuin.ddd4j.ddd.EntityIdPathConverter;
import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventId;
import org.fuin.objects4j.common.Contract;

/**
 * Base class for all commands that are directed to an existing aggregate.
 * 
 * @param <ROOT_ID>
 *            Type of the aggregate root identifier.
 * @param <ENTITY_ID>
 *            Type of the identifier (the last one in the path).
 */
public abstract class AbstractAggregateCommand<ROOT_ID extends AggregateRootId, ENTITY_ID extends EntityId> extends AbstractCommand
        implements AggregateCommand<ROOT_ID, ENTITY_ID> {

    private static final long serialVersionUID = 1000L;

    @NotNull
    @JsonbTypeAdapter(EntityIdPathConverter.class)
    @JsonbProperty("entity-id-path")
    @XmlJavaTypeAdapter(EntityIdPathConverter.class)
    @XmlElement(name = "entity-id-path")
    private EntityIdPath entityIdPath;

    @Nullable
    @JsonbTypeAdapter(AggregateVersionConverter.class)
    @JsonbProperty("aggregate-version")
    @XmlJavaTypeAdapter(AggregateVersionConverter.class)
    @XmlElement(name = "aggregate-version")
    private AggregateVersion aggregateVersion;

    /**
     * Default constructor for JAXB.
     */
    protected AbstractAggregateCommand() { // NOSONAR Ignore uninitialized fields
        super();
    }

    /**
     * Constructor with aggregate root id and version.
     * 
     * @param aggregateRootId
     *            Aggregate root identifier.
     * @param aggregateVersion
     *            Expected aggregate version.
     */
    public AbstractAggregateCommand(@NotNull final AggregateRootId aggregateRootId, @Nullable final AggregateVersion aggregateVersion) {
        this(new EntityIdPath(aggregateRootId), aggregateVersion);
    }

    /**
     * Constructor with entitiy id path and version.
     * 
     * @param entityIdPath
     *            Path from root aggregate to target entity.
     * @param aggregateVersion
     *            Expected aggregate version.
     */
    public AbstractAggregateCommand(@NotNull final EntityIdPath entityIdPath, @Nullable final AggregateVersion aggregateVersion) {
        super();
        Contract.requireArgNotNull("entityIdPath", entityIdPath);
        this.entityIdPath = entityIdPath;
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * Constructor with event this one responds to. Convenience method to set the correlation and causation identifiers correctly.
     * 
     * @param entityIdPath
     *            Path from root aggregate to target entity.
     * @param aggregateVersion
     *            Expected aggregate version.
     * @param respondTo
     *            Causing event.
     */
    public AbstractAggregateCommand(@NotNull final EntityIdPath entityIdPath, @Nullable final AggregateVersion aggregateVersion,
            @NotNull final Event respondTo) {
        super(respondTo);
        Contract.requireArgNotNull("entityIdPath", entityIdPath);
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
    public AbstractAggregateCommand(@NotNull final EntityIdPath entityIdPath, @Nullable final AggregateVersion aggregateVersion,
            @Nullable final EventId correlationId, @Nullable final EventId causationId) {
        super(correlationId, causationId);
        Contract.requireArgNotNull("entityIdPath", entityIdPath);
        this.entityIdPath = entityIdPath;
        this.aggregateVersion = aggregateVersion;
    }

    @Override
    @NotNull
    public final EntityIdPath getEntityIdPath() {
        return entityIdPath;
    }

    @Override
    @NotNull
    public final ENTITY_ID getEntityId() {
        return entityIdPath.last();
    }

    @Override
    @Nullable
    public final ROOT_ID getAggregateRootId() {
        return entityIdPath.first();
    }

    @Override
    @Nullable
    public final AggregateVersion getAggregateVersion() {
        return aggregateVersion;
    }

    @Override
    @Nullable
    public final Integer getAggregateVersionInteger() {
        if (aggregateVersion == null) {
            return null;
        }
        return aggregateVersion.asBaseType();
    }

}
