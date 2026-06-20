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

import org.fuin.ddd4j.core.SimpleRole;

import java.util.List;

/**
 * Decides if a user has the rights to execute a command.
 */
public interface CommandSecurityFilter {

    /**
     * Determines if a user is authorized to execute the given command.
     *
     * @param command   Command to execute.
     * @param userRoles Security roles the user has assigned.
     * @return {@literal true} if the user is allowed to execute the command.
     */
    boolean authorized(Command command, List<SimpleRole> userRoles);

}
