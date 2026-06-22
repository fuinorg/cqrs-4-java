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
 * Mapping between handler classes and command classes.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface CommandHandlerRegistry {

    /**
     * Tries to find a handler class for a given command class.
     *
     * @param cmdClass Command class to find a handler class for.
     * @return Handler class.
     * @throws CommandHandlerClassNotFoundException No handler class found for the given command class.
     */
    Class<? extends CommandHandler> findHandlerClass(Class<? extends Command> cmdClass) throws CommandHandlerClassNotFoundException;

}
