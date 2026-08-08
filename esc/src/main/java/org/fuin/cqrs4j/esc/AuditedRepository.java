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
package org.fuin.cqrs4j.esc;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.ddd4j.core.AggregateAlreadyExistsException;
import org.fuin.ddd4j.core.AggregateDeletedException;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.ddd4j.core.AggregateRoot;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.AggregateVersionConflictException;
import org.fuin.ddd4j.core.Repository;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Objects;

/**
 * Saves an aggregate and records who did it, by pairing the repository's metadata overloads with the
 * {@link CommandExecutionContext} a command handler already receives.
 * <p>
 * {@link Repository} accepts event metadata only as two loose parameters on {@code add}/{@code update},
 * and {@code org.fuin.ddd4j.esc.EventStoreRepositoryAsync} declares those methods {@code final} - there
 * is no interceptor or supplier to hook the acting user in from the side. Spelling the pair out at every
 * call site works, but the failure mode of forgetting it is an event stored with no attribution at all,
 * discovered only when someone needs the audit trail and finds it empty. Going through this class makes
 * the acting user part of saving rather than something to remember.
 * <p>
 * There is deliberately <b>no</b> overload without a {@link CommandExecutionContext}.
 *
 * @see CommandMeta
 */
@ThreadSafe
public final class AuditedRepository {

    private AuditedRepository() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of a utility class");
    }

    /**
     * Adds a new aggregate, recording the acting user in the metadata of every event it produced.
     *
     * @param repository Repository to add the aggregate to.
     * @param aggregate  Aggregate to add.
     * @param context    Execution context of the command being handled - supplies the acting user.
     *
     * @param <ID> Type of the aggregate root identifier.
     * @param <T>  Type of the aggregate.
     *
     * @throws AggregateAlreadyExistsException The aggregate already exists.
     * @throws AggregateDeletedException       The aggregate was already deleted.
     */
    public static <ID extends AggregateRootId, T extends AggregateRoot<ID>> void add(
            final Repository<ID, T> repository, final T aggregate, final CommandExecutionContext context)
            throws AggregateAlreadyExistsException, AggregateDeletedException {
        Objects.requireNonNull(repository, "repository==null");
        Objects.requireNonNull(aggregate, "aggregate==null");
        repository.add(aggregate, CommandMeta.TYPE.asBaseType(), meta(context));
    }

    /**
     * Saves the changes on an aggregate, recording the acting user in the metadata of every event it
     * produced.
     *
     * @param repository Repository holding the aggregate.
     * @param aggregate  Aggregate to store.
     * @param context    Execution context of the command being handled - supplies the acting user.
     *
     * @param <ID> Type of the aggregate root identifier.
     * @param <T>  Type of the aggregate.
     *
     * @throws AggregateVersionConflictException The expected version did not match the actual version.
     * @throws AggregateNotFoundException        No aggregate with that identifier was found.
     * @throws AggregateDeletedException         The aggregate was already deleted.
     */
    public static <ID extends AggregateRootId, T extends AggregateRoot<ID>> void update(
            final Repository<ID, T> repository, final T aggregate, final CommandExecutionContext context)
            throws AggregateVersionConflictException, AggregateNotFoundException, AggregateDeletedException {
        Objects.requireNonNull(repository, "repository==null");
        Objects.requireNonNull(aggregate, "aggregate==null");
        repository.update(aggregate, CommandMeta.TYPE.asBaseType(), meta(context));
    }

    private static CommandMeta meta(final CommandExecutionContext context) {
        Objects.requireNonNull(context, "context==null");
        return new CommandMeta(context.getUser().getUserId());
    }

}
