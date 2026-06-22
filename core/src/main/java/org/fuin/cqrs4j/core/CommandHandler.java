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

import org.fuin.objects4j.common.ThreadSafe;

/**
 * Handles a single command.
 * All implementations are expected to be thread safe.
 *
 * @param <T> Type of the command.
 * @param <R> Type of result.
 */
@ThreadSafe
public interface CommandHandler<T extends Command, R> {

    /**
     * Returns the type of command.
     *
     * @return Command class this instance handles.
     */
    Class<T> getCommandType();

    /**
     * Processes the command.
     *
     * @param context Context.
     * @param cmd Command to process.
     * @return Result of processing.
     * @throws CommandExecutionFailedException Something went wrong.
     */
    R handle(CommandExecutionContext context, T cmd) throws CommandExecutionFailedException;

}
