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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.fuin.ddd4j.ddd.AbstractEvent;
import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventId;
import org.fuin.esc.api.SimpleCommonEvent;
import org.fuin.esc.api.TypeName;
import org.junit.Test;

//CHECKSTYLE:OFF
public final class SimpleEventDispatcherTest {

    private static final EventType EVENT_TYPE_A = new EventType("EventA");

    private static final EventType EVENT_TYPE_B = new EventType("EventB");

    @Test
    public final void testDispatchEvents() {

        // PREPARE
        final CollectingEventHandler<EventA> handlerA = new CollectingEventHandler<>(
                EVENT_TYPE_A);
        final CollectingEventHandler<EventB> handlerB = new CollectingEventHandler<>(
                EVENT_TYPE_B);
        final EventDispatcher testee = new SimpleEventDispatcher(handlerA,
                handlerB);

        final List<Event> events = new ArrayList<>();
        final EventA a1 = new EventA();
        events.add(a1);
        final EventA a2 = new EventA();
        events.add(a2);
        final EventA a3 = new EventA();
        events.add(a3);
        final EventB b1 = new EventB();
        events.add(b1);
        final EventB b2 = new EventB();
        events.add(b2);

        // TEST
        testee.dispatchEvents(events);

        // VERIFY
        assertThat(handlerA.getEvents()).containsExactly(a1, a2, a3);
        assertThat(handlerB.getEvents()).containsExactly(b1, b2);

    }

    @Test
    public final void testDispatchCommonEvents() {

        // PREPARE
        final CollectingEventHandler<EventA> handlerA = new CollectingEventHandler<>(
                EVENT_TYPE_A);
        final CollectingEventHandler<EventB> handlerB = new CollectingEventHandler<>(
                EVENT_TYPE_B);
        final EventDispatcher testee = new SimpleEventDispatcher(handlerA,
                handlerB);

        final List<CommonEvent> events = new ArrayList<>();
        final EventA a1 = new EventA();
        events.add(asCommonEvent(a1));
        final EventA a2 = new EventA();
        events.add(asCommonEvent(a2));
        final EventA a3 = new EventA();
        events.add(asCommonEvent(a3));
        final EventB b1 = new EventB();
        events.add(asCommonEvent(b1));
        final EventB b2 = new EventB();
        events.add(asCommonEvent(b2));

        // TEST
        testee.dispatchCommonEvents(events);

        // VERIFY
        assertThat(handlerA.getEvents()).containsExactly(a1, a2, a3);
        assertThat(handlerB.getEvents()).containsExactly(b1, b2);

    }

    @Test
    public final void testGetAllTypes() {

        // PREPARE
        final CollectingEventHandler<EventA> handlerA = new CollectingEventHandler<>(
                EVENT_TYPE_A);
        final CollectingEventHandler<EventB> handlerB = new CollectingEventHandler<>(
                EVENT_TYPE_B);
        final EventDispatcher testee = new SimpleEventDispatcher(handlerA,
                handlerB);

        final List<EventType> typeList = new ArrayList<>();
        typeList.add(handlerA.getEventType());
        typeList.add(handlerB.getEventType());

        // TEST & VERIFY
        assertThat(testee.getAllTypes()).hasSameElementsAs(typeList);

    }

    @Test
    public final void testMultipleEventHandlersForOneEvent() {

        // PREPARE
        final CollectingEventHandler<EventA> handlerA1 = new CollectingEventHandler<>(
                EVENT_TYPE_A);
        final CollectingEventHandler<EventA> handlerA2 = new CollectingEventHandler<>(
                EVENT_TYPE_A);
        final CollectingEventHandler<EventB> handlerB = new CollectingEventHandler<>(
                EVENT_TYPE_B);
        final EventDispatcher testee = new SimpleEventDispatcher(handlerA1,
                handlerA2, handlerB);

        final List<Event> events = new ArrayList<>();
        final EventA a1 = new EventA();
        events.add(a1);
        final EventA a2 = new EventA();
        events.add(a2);
        final EventA a3 = new EventA();
        events.add(a3);
        final EventB b1 = new EventB();
        events.add(b1);
        final EventB b2 = new EventB();
        events.add(b2);

        // TEST
        testee.dispatchEvents(events);

        // VERIFY
        assertThat(handlerA1.getEvents()).containsExactly(a1, a2, a3);
        assertThat(handlerA1.getEvents()).containsExactly(a1, a2, a3);
        assertThat(handlerB.getEvents()).containsExactly(b1, b2);

    }

    private static CommonEvent asCommonEvent(final Event event) {
        final EventId eventId = new EventId(event.getEventId().asBaseType());
        final TypeName typeName = new TypeName(
                event.getEventType().asBaseType());
        return new SimpleCommonEvent(eventId, typeName, event);
    }

    private static class EventA extends AbstractEvent {

        private static final long serialVersionUID = 1L;

        @Override
        public EventType getEventType() {
            return EVENT_TYPE_A;
        }

    }

    private static class EventB extends AbstractEvent {

        private static final long serialVersionUID = 1L;

        @Override
        public EventType getEventType() {
            return EVENT_TYPE_B;
        }

    }

    @SuppressWarnings({ "unused" })
    private static class CollectingEventHandler<TYPE extends Event>
            implements EventHandler<TYPE> {

        private EventType type;

        private List<Event> events;

        public CollectingEventHandler(EventType type) {
            super();
            this.type = type;
            this.events = new ArrayList<Event>();
        }

        @Override
        public EventType getEventType() {
            return type;
        }

        @Override
        public void handle(TYPE event) {
            events.add(event);
        }

        @SuppressWarnings("unused")
        public EventType getType() {
            return type;
        }

        public List<Event> getEvents() {
            return events;
        }

    }

}
// CHECKSTYLE:ON
