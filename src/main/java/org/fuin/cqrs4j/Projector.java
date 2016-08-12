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

import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.ReadableEventStore;
import org.fuin.esc.api.StreamDeletedException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.StreamNotFoundException;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads events from a stream and calls the appropriate event handlers.
 */
public final class Projector {

    private static final Logger LOG = LoggerFactory.getLogger(Projector.class);

    private final ReadableEventStore eventStore;

    private final StreamId streamId;

    private final int streamReadPageSize;

    private final ProjectionService projectionService;

    private final Map<EventType, EventHandler> eventHandlers;

    /**
     * Constructor with event array.
     * 
     * @param eventStore
     *            Event store used for reading the events.
     * @param streamId
     *            Unique identifier of the stream the projector reads to update
     *            the view.
     * @param streamReadPageSize
     *            Page size to read.
     * @param projectionService
     *            Used to store the position of the projection.
     * @param eventHandlers
     *            Array of event handlers.
     */
    public Projector(@NotNull final ReadableEventStore eventStore, @NotNull final StreamId streamId,
            final int streamReadPageSize, @NotNull final ProjectionService projectionService,
            @NotNull final EventHandler... eventHandlers) {
        this(eventStore, streamId, streamReadPageSize, projectionService, Arrays.asList(eventHandlers));
    }

    /**
     * Constructor with event list.
     * 
     * @param eventStore
     *            Event store used for reading the events.
     * @param streamId
     *            Unique identifier of the stream the projector reads to update
     *            the view.
     * @param streamReadPageSize
     *            Page size to read.
     * @param projectionService
     *            Used to store the position of the projection.
     * @param eventHandlers
     *            List of event handlers.
     */
    public Projector(@NotNull final ReadableEventStore eventStore, @NotNull final StreamId streamId,
            final int streamReadPageSize, @NotNull final ProjectionService projectionService,
            @NotNull final List<EventHandler> eventHandlers) {
        super();
        Contract.requireArgNotNull("eventStore", eventStore);
        Contract.requireArgNotNull("streamId", streamId);
        Contract.requireArgNotNull("projectionService", projectionService);
        Contract.requireArgNotNull("eventHandlers", eventHandlers);
        if (eventHandlers.size() == 0) {
            throw new ConstraintViolationException("The argument 'eventHandlers' cannot be an empty list");
        }
        this.eventHandlers = new HashMap<>();
        for (final EventHandler eventHandler : eventHandlers) {
            if (this.eventHandlers.containsKey(eventHandler.getEventType())) {
                throw new ConstraintViolationException(
                        "The argument 'eventHandlers' contains multiple handlers for event: "
                                + eventHandler.getEventType());
            }
            this.eventHandlers.put(eventHandler.getEventType(), eventHandler);
        }
        this.eventStore = eventStore;
        this.streamId = streamId;
        this.streamReadPageSize = streamReadPageSize;
        this.projectionService = projectionService;
    }

    /**
     * Reads the new events from a stream that has all the events for the view.
     */
    public final void readStreamEvents() {
        LOG.debug("Read stream events: {}", streamId);

        final Integer nextEventNumber = projectionService.readProjectionPosition(streamId);

        int sliceStart = nextEventNumber;
        StreamEventsSlice currentSlice;
        do {
            final int sliceInc = streamReadPageSize;
            final int sliceCount = sliceStart + sliceInc;
            try {
                LOG.debug("Read slice: streamId={}, sliceStart={}, sliceCount={}", streamId, sliceStart,
                        sliceCount);
                currentSlice = eventStore.readEventsForward(streamId, sliceStart, sliceCount);
                LOG.debug("Result slice: {}", currentSlice);
            } catch (final StreamNotFoundException ex) {
                throw new RuntimeException(ex);
            } catch (final StreamDeletedException ex) {
                throw new RuntimeException(ex);
            }
            if (currentSlice.getEvents().size() > 0) {
                for (final CommonEvent commonEvent : currentSlice.getEvents()) {
                    final Event event = (Event) commonEvent.getData();
                    handle(event);
                }
                projectionService.updateProjectionPosition(streamId, currentSlice.getNextEventNumber());
            }
            sliceStart = currentSlice.getNextEventNumber();
        } while (!currentSlice.isEndOfStream());

    }

    /**
     * Handles the given event. Events the projector is not interested in are
     * ignored.
     * 
     * @param event
     *            Event to apply to the view.
     */
    private void handle(final Event event) {
        final EventHandler handler = eventHandlers.get(event.getEventType());
        if (handler != null) {
            handler.handle(event);
        }
    }

}
