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
import org.fuin.ddd4j.core.Event;
import org.fuin.objects4j.common.NotThreadSafe;

import java.util.List;

/**
 * Reacts to domain events by changing its own (event-sourced) state and producing commands. A process
 * manager instance is single threaded - a single instance is expected to be loaded, used and saved within
 * one thread.
 */
@NotThreadSafe
public interface ProcessManager {

    /**
     * Lets the process manager react to a single domain event. The implementation typically records its own
     * state change(s) and buffers any resulting command(s) via {@link #getCommandsToSend()}.
     *
     * @param event Event the process manager should react to.
     */
    void handle(Event event);

    /**
     * Returns the commands that were produced by previous {@link #handle(Event)} calls and not yet sent.
     *
     * @return Unmodifiable list of commands to send (never {@code null}, but may be empty).
     */
    List<Command> getCommandsToSend();

    /**
     * Clears the buffer of commands to send. Called after the commands have been dispatched.
     */
    void clearCommandsToSend();

}
