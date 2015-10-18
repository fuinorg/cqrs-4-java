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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.ReadableEventStoreSync;
import org.fuin.esc.api.StreamDeletedException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.StreamNotFoundException;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ContractViolationException;
import org.fuin.objects4j.common.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides basic functionality to the concrete projectors.
 */
public abstract class AbstractProjector implements Projector {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProjector.class);

    private final String viewId;

    private final String viewName;

    private final String viewDescription;

    private final StreamId streamId;

    private int streamReadPageSize;

    private final Map<EventType, EventHandler> eventHandlers;

    private final Set<EventType> eventTypes;

    /**
     * Constructor with event array.
     * 
     * @param viewId
     *            Unique view identifier.
     * @param viewName
     *            Unique view name.
     * @param viewDescription
     *            Unique view description.
     * @param streamId
     *            Unique identifier of the stream the projector reads to update the view.
     * @param streamReadPageSize
     *            Page size to read.
     * @param eventHandlers
     *            Array of event handlers.
     */
    public AbstractProjector(@NotEmpty final String viewId, @NotEmpty final String viewName,
            @NotEmpty final String viewDescription, @NotNull final StreamId streamId,
            final int streamReadPageSize, @NotNull final EventHandler... eventHandlers) {
        this(viewId, viewName, viewDescription, streamId, streamReadPageSize, Arrays.asList(eventHandlers));
    }

    /**
     * Constructor with event list.
     * 
     * @param viewId
     *            Unique view identifier.
     * @param viewName
     *            Unique view name.
     * @param viewDescription
     *            Unique view description.
     * @param streamId
     *            Unique identifier of the stream the projector reads to update the view.
     * @param streamReadPageSize
     *            Page size to read.
     * @param eventHandlers
     *            List of event handlers.
     */
    public AbstractProjector(@NotEmpty final String viewId, @NotEmpty final String viewName,
            @NotEmpty final String viewDescription, @NotNull final StreamId streamId,
            final int streamReadPageSize, @NotNull final List<EventHandler> eventHandlers) {
        super();
        Contract.requireArgNotEmpty("viewId", viewId);
        Contract.requireArgNotEmpty("viewName", viewName);
        Contract.requireArgNotEmpty("viewDescription", viewDescription);
        Contract.requireArgNotNull("streamId", streamId);
        Contract.requireArgNotNull("eventHandlers", eventHandlers);
        if (eventHandlers.size() == 0) {
            throw new ContractViolationException("The argument 'eventHandlers' cannot be an empty list");
        }
        this.eventHandlers = new HashMap<>();
        for (final EventHandler eventHandler : eventHandlers) {
            if (this.eventHandlers.containsKey(eventHandler.getEventType())) {
                throw new ContractViolationException(
                        "The argument 'eventHandlers' contains multiple handlers for event: "
                                + eventHandler.getEventType());
            }
            this.eventHandlers.put(eventHandler.getEventType(), eventHandler);
        }
        this.viewId = viewId;
        this.viewName = viewName;
        this.viewDescription = viewDescription;
        this.streamId = streamId;
        this.streamReadPageSize = streamReadPageSize;
        this.eventTypes = Collections.unmodifiableSet(this.eventHandlers.keySet());
    }

    @Override
    public String getViewId() {
        return viewId;
    }

    @Override
    public String getViewName() {
        return viewName;
    }

    @Override
    public String getViewDescription() {
        return viewDescription;
    }

    @Override
    public Set<EventType> getEventTypes() {
        return eventTypes;
    }

    /**
     * Reads the new events from a stream that has all the events for the view.
     */
    protected void readStreamEvents() {
        LOG.debug("Read stream events: {}", streamId);

        final Integer nextEventNumber = readProjectionPosition(streamId);

        int sliceStart = nextEventNumber;
        StreamEventsSlice currentSlice;
        do {
            final int sliceInc = streamReadPageSize;
            final int sliceCount = sliceStart + sliceInc;
            try {
                LOG.debug("Read slice: streamId={}, sliceStart={}, sliceCount={}", streamId, sliceStart,
                        sliceCount);
                currentSlice = getEventStore().readEventsForward(streamId, sliceStart, sliceCount);
                LOG.debug("Result slice: {}", currentSlice);
            } catch (final StreamNotFoundException ex) {
                throw new RuntimeException(ex);
            } catch (final StreamDeletedException ex) {
                throw new RuntimeException(ex);
            }
            for (final CommonEvent commonEvent : currentSlice.getEvents()) {
                final Event event = (Event) commonEvent.getData();
                handle(event);
            }
            if (nextEventNumber.compareTo(currentSlice.getNextEventNumber()) != 0) {
                // Only update if it really changed
                updateProjectionPosition(streamId, currentSlice.getNextEventNumber());
            }
            sliceStart = currentSlice.getNextEventNumber();
        } while (!currentSlice.isEndOfStream());

    }

    @Override
    public void handle(@NotNull final Event event) {
        final EventHandler handler = eventHandlers.get(event.getEventType());
        if (handler != null) {
            handler.handle(event);
        }
    }

    /**
     * Reads the position that was read last time.
     * 
     * @param streamId
     *            Unique ID of the stream.
     * 
     * @return Number of the next event to read.
     */
    protected abstract Integer readProjectionPosition(StreamId streamId);

    /**
     * Updates the position to read next time.
     * 
     * @param streamId
     *            Unique ID of the stream.
     * @param nextEventNumber
     *            Number of the next event to read.
     */
    protected abstract void updateProjectionPosition(StreamId streamId, Integer nextEventNumber);

    /**
     * Returns the event store used to read.
     * 
     * @return Event store for reading events.
     */
    protected abstract ReadableEventStoreSync getEventStore();

}
