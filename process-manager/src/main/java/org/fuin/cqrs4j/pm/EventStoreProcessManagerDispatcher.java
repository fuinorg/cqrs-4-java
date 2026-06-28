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
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.esc.IEventStoreRepositoryAsync;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.esc.api.Subscription;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Drives one or more process managers from an asynchronous, subscribable event store: it subscribes to the
 * configured stream(s), and for every delivered event it correlates the event to a process manager instance,
 * loads it, lets it {@link ProcessManager#handle(Event) react}, saves it and dispatches the resulting commands
 * through the {@link CommandBus}.
 *
 * @param <ID> Type of the process manager identifier.
 * @param <PM> Concrete process manager type.
 */
@ThreadSafe
public final class EventStoreProcessManagerDispatcher<ID extends AggregateRootId, PM extends AbstractProcessManager<ID>> {

    private static final Logger LOG = LoggerFactory.getLogger(EventStoreProcessManagerDispatcher.class);

    private final SubscribableEventStoreAsync eventStore;

    private final IEventStoreRepositoryAsync<ID, PM> repository;

    private final CommandBus commandBus;

    private final Function<Event, Optional<ID>> correlation;

    private final List<StreamId> streams;

    private final long fromEventNumber;

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * Constructor with all mandatory data.
     *
     * @param eventStore      Subscribable event store the events are received from.
     * @param repository      Repository used to load/save the process managers.
     * @param commandBus      Bus the resulting commands are sent to.
     * @param correlation     Maps an incoming event to the identifier of the process manager that should react
     *                        to it ({@link Optional#empty()} = ignore the event).
     * @param streams         Streams to subscribe to.
     * @param fromEventNumber Position to start the subscription at (e.g. 0 = from the first event,
     *                        {@code EscApiUtils.SUBSCRIBE_TO_NEW_EVENTS} = only new events).
     */
    public EventStoreProcessManagerDispatcher(final SubscribableEventStoreAsync eventStore,
                                              final IEventStoreRepositoryAsync<ID, PM> repository,
                                              final CommandBus commandBus,
                                              final Function<Event, Optional<ID>> correlation,
                                              final List<StreamId> streams, final long fromEventNumber) {
        Contract.requireArgNotNull("eventStore", eventStore);
        Contract.requireArgNotNull("repository", repository);
        Contract.requireArgNotNull("commandBus", commandBus);
        Contract.requireArgNotNull("correlation", correlation);
        Contract.requireArgNotNull("streams", streams);
        this.eventStore = eventStore;
        this.repository = repository;
        this.commandBus = commandBus;
        this.correlation = correlation;
        this.streams = List.copyOf(streams);
        this.fromEventNumber = fromEventNumber;
    }

    /**
     * Opens a catch-up subscription on every configured stream.
     *
     * @return Future that completes once all subscriptions are established.
     */
    public CompletableFuture<Void> start() {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (final StreamId streamId : streams) {
            futures.add(eventStore.subscribeToStream(streamId, fromEventNumber, this::onEvent, this::onDrop)
                    .thenAccept(subscriptions::add));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Closes all open subscriptions.
     *
     * @return Future that completes once all subscriptions are closed.
     */
    public CompletableFuture<Void> stop() {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (final Subscription subscription : subscriptions) {
            futures.add(eventStore.unsubscribeFromStream(subscription));
        }
        subscriptions.clear();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private void onEvent(final Subscription subscription, final CommonEvent commonEvent) {
        if (!(commonEvent.getData() instanceof Event event)) {
            return;
        }
        final Optional<ID> id = correlation.apply(event);
        if (id.isEmpty()) {
            return;
        }
        try {
            final PM pm = loadOrCreate(id.get());
            pm.handle(event);
            final List<Command> commands = new ArrayList<>(pm.getCommandsToSend());
            pm.clearCommandsToSend();
            repository.update(pm).join();
            for (final Command command : commands) {
                commandBus.send(command);
            }
        } catch (final RuntimeException ex) { // NOSONAR - a failing event must not kill the subscription
            LOG.error("Failed to handle event '{}' for process manager '{}'", event.getEventType(), id.get(), ex);
        }
    }

    private PM loadOrCreate(final ID id) {
        try {
            return repository.read(id).join();
        } catch (final CompletionException ex) {
            if (unwrap(ex) instanceof AggregateNotFoundException) {
                // Process manager reacts to its first event - create a new instance
                return repository.create();
            }
            throw ex;
        }
    }

    private static Throwable unwrap(final Throwable throwable) {
        Throwable cause = throwable;
        while (((cause instanceof CompletionException) || (cause instanceof ExecutionException))
                && (cause.getCause() != null) && (cause.getCause() != cause)) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void onDrop(final Subscription subscription, final Exception exception) {
        LOG.warn("Subscription to stream '{}' was dropped", subscription.getStreamId(), exception);
    }

}
