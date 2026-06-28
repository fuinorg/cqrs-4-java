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
import org.fuin.ddd4j.core.AbstractAggregateRoot;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.NotThreadSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for event-sourced process managers. The process manager's own state is rebuilt from its own
 * domain events (via {@link AbstractAggregateRoot}); reaction methods both {@link #apply(org.fuin.ddd4j.core.DomainEvent)}
 * a state event and {@link #send(Command)} the resulting commands. A ddd4j
 * {@code EventStoreRepositoryAsync} persists/restores the state and the
 * {@code EventStoreProcessManagerDispatcher} drains and dispatches the buffered commands.
 *
 * @param <ID> Type of the process manager identifier.
 */
@NotThreadSafe
public abstract class AbstractProcessManager<ID extends AggregateRootId> extends AbstractAggregateRoot<ID>
        implements ProcessManager {

    private final List<Command> commandsToSend = new ArrayList<>();

    /**
     * Buffers a command that should be sent as a reaction to a handled event.
     *
     * @param command Command to send.
     */
    protected final void send(final Command command) {
        Contract.requireArgNotNull("command", command);
        commandsToSend.add(command);
    }

    @Override
    public final List<Command> getCommandsToSend() {
        return Collections.unmodifiableList(commandsToSend);
    }

    @Override
    public final void clearCommandsToSend() {
        commandsToSend.clear();
    }

}
