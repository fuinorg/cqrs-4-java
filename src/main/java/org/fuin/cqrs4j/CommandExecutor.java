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

import org.fuin.ddd4j.ddd.AggregateAlreadyExistsException;
import org.fuin.ddd4j.ddd.AggregateDeletedException;
import org.fuin.ddd4j.ddd.AggregateNotFoundException;
import org.fuin.ddd4j.ddd.AggregateVersionConflictException;
import org.fuin.ddd4j.ddd.AggregateVersionNotFoundException;
import org.fuin.ddd4j.ddd.EventType;

/**
 * Executes one or more commands.
 * 
 * @param <CONTEXT>
 *            Type of context for the command execution.
 * @param <RESULT>
 *            Result of the command execution.
 * @param <CMD>
 *            Type of command to execute.
 */
public interface CommandExecutor<CONTEXT, RESULT, CMD extends Command> {

    /**
     * Returns a list of commands this executor can handle.
     * 
     * @return List of unique command types.
     */
    @NotNull
    public Set<EventType> getCommandTypes();

    /**
     * Executes the given command. Only the main aggregate related exceptions are modeled via throws. All other checked exceptions must be
     * wrapped into a {@link CommandExecutionFailedException}.
     * 
     * @param ctx
     *            Context of the execute.
     * @param cmd
     *            Command to execute.
     * 
     * @return Result.
     * 
     * @throws AggregateVersionConflictException
     *             There is a conflict between an expected and an actual version for the aggregate targeted by the command.
     * @throws AggregateNotFoundException
     *             The aggregate targeted by the command with a given type and identifier was not found in the repository.
     * @throws AggregateVersionNotFoundException
     *             The requested version for the aggregate targeted by the command does not exist.
     * @throws AggregateDeletedException
     *             The aggregate targeted by the command was deleted from the repository.
     * @throws AggregateAlreadyExistsException
     *             The aggregate targeted by the command already exists when trying to create it.
     * @throws CommandExecutionFailedException
     *             Other checked exceptions are wrapped into this one.
     */
    public RESULT execute(@NotNull CONTEXT ctx, @NotNull CMD cmd) throws AggregateVersionConflictException, AggregateNotFoundException,
            AggregateVersionNotFoundException, AggregateDeletedException, AggregateAlreadyExistsException, CommandExecutionFailedException;

}
