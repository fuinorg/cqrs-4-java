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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotEmpty;

import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.Contract;

/**
 * Handles multiple commands by delegating the call to other executors.
 * 
 * @param <CONTEXT>
 *            Type of context for the command execution.
 * @param <RESULT>
 *            Result of the command execution.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public final class MultiCommandExecutor<CONTEXT, RESULT> implements CommandExecutor<CONTEXT, RESULT, Command> {

    private final Map<EventType, CommandExecutor> commandExecutors;

    /**
     * Constructor with command handler array.
     * 
     * @param cmdExecutors
     *            Array of command executors.
     */
    public MultiCommandExecutor(@NotEmpty final CommandExecutor... cmdExecutors) {
        this(Arrays.asList(cmdExecutors));
    }

    /**
     * Constructor with mandatory data.
     * 
     * @param cmdExecutors
     *            List of command executors.
     */
    public MultiCommandExecutor(@NotEmpty final List<CommandExecutor> cmdExecutors) {
        super();
        Contract.requireArgNotNull("cmdExecutors", cmdExecutors);
        if (cmdExecutors.size() == 0) {
            throw new ConstraintViolationException("The argument 'cmdExecutors' cannot be an empty list");
        }
        this.commandExecutors = new HashMap<>();
        for (final CommandExecutor cmdExecutor : cmdExecutors) {
            if (cmdExecutor == null) {
                throw new ConstraintViolationException("Null is not allowed in the list of 'cmdExecutors': " + cmdExecutors);
            }
            final Set<EventType> cmdTypes = cmdExecutor.getCommandTypes();
            for (final EventType cmdType : cmdTypes) {
                if (this.commandExecutors.containsKey(cmdType)) {
                    throw new ConstraintViolationException(
                            "The argument 'cmdExecutors' contains multiple executors for command: " + cmdType);
                }
                this.commandExecutors.put(cmdType, cmdExecutor);
            }
        }
    }

    @Override
    public final Set<EventType> getCommandTypes() {
        return commandExecutors.keySet();
    }

    @Override
    public final RESULT execute(final CONTEXT ctx, final Command cmd) {
        Contract.requireArgNotNull("ctx", ctx);
        Contract.requireArgNotNull("cmd", cmd);
        final CommandExecutor<CONTEXT, RESULT, Command> cmdExecutor = commandExecutors.get(cmd.getEventType());
        if (cmdExecutor == null) {
            throw new IllegalArgumentException("No executor found for command: " + cmd.getEventType());
        }
        return cmdExecutor.execute(ctx, cmd);
    }

}
