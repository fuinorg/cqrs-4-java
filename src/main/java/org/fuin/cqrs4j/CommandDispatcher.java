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

import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.Contract;

/**
 * Dispatches commands to their handlers.
 */
public final class CommandDispatcher {

    @SuppressWarnings("rawtypes")
    private final Map<EventType, CommandHandler> commandHandlers;

    /**
     * Constructor with command handler array.
     * 
     * @param commandHandlers
     *            Array of command handlers.
     */
    @SuppressWarnings("rawtypes")
    public CommandDispatcher(@NotNull final CommandHandler... commandHandlers) {
        this(commandHandlers == null ? null : Arrays.asList(commandHandlers));
    }

    /**
     * Constructor with mandatory data.
     * 
     * @param commandHandlers
     *            List of command handlers.
     */
    @SuppressWarnings("rawtypes")
    public CommandDispatcher(@NotNull final List<CommandHandler> commandHandlers) {
        super();
        Contract.requireArgNotNull("commandHandlers", commandHandlers);
        if (commandHandlers.size() == 0) {
            throw new ConstraintViolationException("The argument 'commandHandlers' cannot be an empty list");
        }
        this.commandHandlers = new HashMap<>();
        for (final CommandHandler commandHandler : commandHandlers) {
            if (commandHandler == null) {
                throw new ConstraintViolationException(
                        "Null is not allowed in the list of 'commandHandlers': " + commandHandlers);
            }
            if (this.commandHandlers.containsKey(commandHandler.getEventType())) {
                throw new ConstraintViolationException(
                        "The argument 'commandHandlers' contains multiple handlers for event: "
                                + commandHandler.getEventType());
            }
            this.commandHandlers.put(commandHandler.getEventType(), commandHandler);
        }
    }

    /**
     * Dispatches the given command.
     * 
     * @param command
     *            Command to dispatch.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final void dispatch(@NotNull final Command command) {
        Contract.requireArgNotNull("command", command);
        final CommandHandler handler = commandHandlers.get(command.getEventType());
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for command: " + command.getEventType());
        }
        handler.handle(command);
    }

}
