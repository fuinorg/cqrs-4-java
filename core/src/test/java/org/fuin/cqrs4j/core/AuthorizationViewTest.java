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

import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AuthorizationView}.
 */
public class AuthorizationViewTest {

    private static final EventType GRANTED = new EventType("PermissionsGrantedEvent");

    private static final EventType JOINED = new EventType("TeamJoinedEvent");

    private static final TenantId T = new TenantId("acme");

    @Test
    public void testEventTypesAreTheUnionOverAllSources() {
        // The point of a multi-source projection: a second source widens the selection without the view
        // changing.
        final AuthorizationView testee = new AuthorizationView(
                List.of(new RecordingSource(Set.of(GRANTED)), new RecordingSource(Set.of(JOINED))),
                new PermissionState(), "*/5 * * * * *", T);
        assertThat(testee.getEventTypes()).containsExactlyInAnyOrder(GRANTED, JOINED);
    }

    @Test
    public void testEveryEventIsOfferedToEverySource() {
        // A source sees types another source registered, and ignores what it does not recognise. That is what
        // keeps sources independent of one another.
        final RecordingSource first = new RecordingSource(Set.of(GRANTED));
        final RecordingSource second = new RecordingSource(Set.of(JOINED));
        final AuthorizationView testee = new AuthorizationView(List.of(first, second), new PermissionState(),
                "*/5 * * * * *", T);

        final Event event = new TestEvent(GRANTED);
        testee.handleEvents(List.of(event));

        assertThat(first.seen).containsExactly(event);
        assertThat(second.seen).containsExactly(event);
    }

    @Test
    public void testTheFoldIsToldWhichTenantTheEventsCameFrom() {
        // An event carries no tenant of its own - which stream it came from is known only to the caller - so
        // a source that had to guess would attribute one tenant's grants to another.
        final RecordingSource source = new RecordingSource(Set.of(GRANTED));
        final AuthorizationView testee = new AuthorizationView(List.of(source), new PermissionState(),
                "*/5 * * * * *", T);
        final TenantId other = new TenantId("other");

        testee.handleEvents(other, List.of(new TestEvent(GRANTED)));

        assertThat(source.tenants).containsExactly(other);
    }

    @Test
    public void testTheTenantLessViewMethodUsesTheDefaultTenant() {
        // View.handleEvents(List) has no tenant parameter, so it has to fall back to the one the view was
        // built with rather than to none.
        final RecordingSource source = new RecordingSource(Set.of(GRANTED));
        final AuthorizationView testee = new AuthorizationView(List.of(source), new PermissionState(),
                "*/5 * * * * *", T);

        testee.handleEvents(List.of(new TestEvent(GRANTED)));

        assertThat(source.tenants).containsExactly(T);
    }

    @Test
    public void testRefusesToBeBuiltWithoutASource() {
        // A projection with no source selects no events and would answer "holds nothing" for everybody -
        // indistinguishable from a correctly configured system in which nobody has been granted anything.
        assertThatThrownBy(() -> new AuthorizationView(List.of(), new PermissionState(), "*/5 * * * * *", T))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be an empty list");
    }

    @Test
    public void testRefusesToBeBuiltWhenNoSourceDeclaresAnEventType() {
        assertThatThrownBy(() -> new AuthorizationView(List.of(new RecordingSource(Set.of())),
                new PermissionState(), "*/5 * * * * *", T))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declares an event type");
    }

    @Test
    public void testProjectionAndStreamNamesAreStable() {
        // The id must not vary per deployment, or two instances create two projections of the same thing.
        final AuthorizationView testee = view();
        assertThat(testee.getName()).isEqualTo("Authorization");
        assertThat(testee.getProjectionName()).isEqualTo("AuthorizationProjection");
        assertThat(testee.getStreamName()).isEqualTo("AuthorizationProjection");
        assertThat(testee.getBeanClass()).isEqualTo(AuthorizationView.class);
        assertThat(testee.getBeanName()).isEqualTo("authorizationView");
        assertThat(testee.getCron()).isEqualTo("*/5 * * * * *");
    }

    @Test
    public void testStateIsExposed() {
        final PermissionState state = new PermissionState();
        assertThat(new AuthorizationView(List.of(new RecordingSource(Set.of(GRANTED))), state, "*/5 * * * * *", T)
                .getState()).isSameAs(state);
    }

    private static AuthorizationView view() {
        return new AuthorizationView(List.of(new RecordingSource(Set.of(GRANTED))), new PermissionState(),
                "*/5 * * * * *", T);
    }

    private static final class RecordingSource implements PermissionEventSource {

        private final Set<EventType> types;

        private final List<Event> seen = new ArrayList<>();

        private final List<TenantId> tenants = new ArrayList<>();

        private RecordingSource(final Set<EventType> types) {
            this.types = types;
        }

        @Override
        public Set<EventType> eventTypes() {
            return types;
        }

        @Override
        public void apply(final TenantId tenantId, final Event event, final PermissionState state) {
            seen.add(event);
            tenants.add(tenantId);
        }

    }

    private static final class TestEvent implements Event {

        @Serial
        private static final long serialVersionUID = 1L;

        private final EventType eventType;

        private TestEvent(final EventType eventType) {
            this.eventType = eventType;
        }

        @Override
        public EventId getEventId() {
            return new EventId();
        }

        @Override
        public EventType getEventType() {
            return eventType;
        }

        @Override
        public ZonedDateTime getEventTimestamp() {
            return ZonedDateTime.now();
        }

        @Override
        @Nullable
        public EventId getCorrelationId() {
            return null;
        }

        @Override
        @Nullable
        public EventId getCausationId() {
            return null;
        }

    }

}
