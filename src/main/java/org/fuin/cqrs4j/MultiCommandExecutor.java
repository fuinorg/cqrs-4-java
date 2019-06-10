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

import java.util.List;

import javax.validation.constraints.NotEmpty;

/**
 * Handles multiple commands by delegating the call to other executors.
 * 
 * @param <CONTEXT>
 *            Type of context for the command execution.
 * @param <RESULT>
 *            Result of the command execution.
 */
@SuppressWarnings("rawtypes")
public final class MultiCommandExecutor<CONTEXT, RESULT> extends AbstractMultiCommandExecutor<CONTEXT, RESULT> {

    /**
     * Constructor with command handler array.
     * 
     * @param cmdExecutors
     *            Array of command executors.
     */
    public MultiCommandExecutor(@NotEmpty final CommandExecutor... cmdExecutors) {
        super(cmdExecutors);
    }

    /**
     * Constructor with mandatory data.
     * 
     * @param cmdExecutors
     *            List of command executors.
     */
    public MultiCommandExecutor(@NotEmpty final List<CommandExecutor> cmdExecutors) {
        super(cmdExecutors);
    }

}
