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
package org.fuin.cqrs4j.pm;

import org.fuin.cqrs4j.core.Command;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory {@link CommandBus} that routes a command to a handler registered for its concrete type.
 */
@ThreadSafe
public final class SimpleCommandBus implements CommandBus {

    private final Map<Class<? extends Command>, Consumer<? super Command>> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a handler for a command type. Only one handler per type is supported; registering a second
     * one replaces the first.
     *
     * @param type    Command type to handle.
     * @param handler Handler that executes commands of that type.
     * @param <C>     Concrete command type.
     */
    @SuppressWarnings("unchecked")
    public <C extends Command> void register(final Class<C> type, final Consumer<? super C> handler) {
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("handler", handler);
        handlers.put(type, (Consumer<? super Command>) handler);
    }

    @Override
    public void send(final Command command) {
        Contract.requireArgNotNull("command", command);
        final Consumer<? super Command> handler = handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for command: " + command.getClass().getName());
        }
        handler.accept(command);
    }

}
