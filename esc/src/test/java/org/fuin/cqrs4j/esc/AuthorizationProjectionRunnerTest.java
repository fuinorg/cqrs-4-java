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
package org.fuin.cqrs4j.esc;

import org.fuin.cqrs4j.core.AuthorizationView;
import org.fuin.cqrs4j.core.PermissionEventSource;
import org.fuin.cqrs4j.core.PermissionState;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.ProjectionId;
import org.fuin.esc.api.StreamId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AuthorizationProjectionRunner}.
 */
public class AuthorizationProjectionRunnerTest {

    private static final EventType GRANTED = new EventType("PermissionsGrantedEvent");

    private static final TenantId T = new TenantId("acme");

    @Test
    public void testNotReadyBeforeTheFirstCatchUp() {
        // Fail closed: an authorizer asking before the first pass must be told "unknown", not "holds nothing".
        try (AuthorizationProjectionRunner testee = runner(mock(EventStore.class), admin(), Duration.ofSeconds(5),
                Duration.ofSeconds(30))) {
            assertThat(testee.ready()).isFalse();
            assertThat(testee.lastCatchUp()).isNull();
        }
    }

    @Test
    public void testReadyAfterAPassOverAnEmptyStream() {
        // No events yet is a complete answer - nobody holds anything - so it counts as caught up rather than
        // denying every request until somebody is granted something.
        final EventStore eventStore = mock(EventStore.class);
        when(eventStore.streamExists(ArgumentMatchers.<StreamId>any())).thenReturn(false);

        try (AuthorizationProjectionRunner testee = runner(eventStore, admin(), Duration.ofMillis(50),
                Duration.ofSeconds(30))) {
            testee.start();
            awaitTrue(testee::ready, "the first catch-up to complete");
            assertThat(testee.lastCatchUp()).isNotNull();
        }
    }

    @Test
    public void testGoesNotReadyOnceTheLastCatchUpIsTooOld() {
        // A store that stops answering must turn into denials rather than an ever-staler snapshot that keeps
        // honouring a permission somebody has already revoked.
        final EventStore eventStore = mock(EventStore.class);
        when(eventStore.streamExists(ArgumentMatchers.<StreamId>any())).thenReturn(false);

        try (AuthorizationProjectionRunner testee = runner(eventStore, admin(), Duration.ofMillis(50),
                Duration.ofMillis(200))) {
            testee.start();
            awaitTrue(testee::ready, "the first catch-up to complete");
            testee.close();
            // Nothing refreshes it any more, so it must fall out of the staleness window.
            awaitTrue(() -> !testee.ready(), "the snapshot to be declared stale");
        }
    }

    @Test
    public void testAnUnreachableStoreDoesNotStopTheRunner() {
        // scheduleWithFixedDelay cancels a task that throws, so an escaping exception would silently stop the
        // projection for good. It must keep trying, and keep reporting not-ready in the meantime.
        final AtomicInteger attempts = new AtomicInteger();
        final EventStore eventStore = mock(EventStore.class);
        when(eventStore.streamExists(ArgumentMatchers.<StreamId>any())).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new RuntimeException("store is down");
        });

        try (AuthorizationProjectionRunner testee = runner(eventStore, admin(), Duration.ofMillis(50),
                Duration.ofSeconds(30))) {
            testee.start();
            awaitTrue(() -> attempts.get() >= 2, "the runner to retry after a failure");
            assertThat(testee.ready()).isFalse();
        }
    }

    @Test
    public void testCreatesTheProjectionWhenItDoesNotExist() {
        final EventStore eventStore = mock(EventStore.class);
        when(eventStore.streamExists(ArgumentMatchers.<StreamId>any())).thenReturn(false);
        final ProjectionAdminEventStore admin = admin();

        try (AuthorizationProjectionRunner testee = runner(eventStore, admin, Duration.ofMillis(50),
                Duration.ofSeconds(30))) {
            testee.start();
            awaitTrue(testee::ready, "the first catch-up to complete");
            verify(admin).createProjection(ArgumentMatchers.<ProjectionId>any(), ArgumentMatchers.any(),
                    ArgumentMatchers.eq(true), ArgumentMatchers.anyList(), ArgumentMatchers.anyList());
        }
    }

    @Test
    public void testRefusesAStalenessLimitShorterThanThePollInterval() {
        // Otherwise the runner reports stale between two perfectly healthy polls and denies everything.
        assertThatThrownBy(() -> runner(mock(EventStore.class), admin(), Duration.ofSeconds(5),
                Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStaleness");
    }

    @Test
    public void testRefusesANonPositivePollInterval() {
        assertThatThrownBy(() -> runner(mock(EventStore.class), admin(), Duration.ZERO, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pollInterval");
    }

    @Test
    public void testRefusesToStartWhenMultitenancyIsEnabled() {
        // Every link in this stack's tenancy chain degrades silently to single-tenant when it is missing.
        // This one must not: projecting one tenant and denying every other looks like a permissions problem,
        // not like a missing feature, and it would be diagnosed as one.
        try (AuthorizationProjectionRunner testee = runner(mock(EventStore.class), admin(),
                Duration.ofSeconds(5), Duration.ofSeconds(30), true)) {
            assertThatThrownBy(testee::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not tenant-aware");
            assertThat(testee.ready()).isFalse();
        }
    }

    /**
     * Polls until the condition holds. A local helper rather than a new test dependency on a library, for
     * the three places this test has to wait for a background thread.
     *
     * @param condition What to wait for.
     * @param what      Description used in the failure message.
     */
    private static void awaitTrue(final BooleanSupplier condition, final String what) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + what, ex);
            }
        }
        fail("Timed out after 5s waiting for " + what);
    }

    private static ProjectionAdminEventStore admin() {
        final ProjectionAdminEventStore admin = mock(ProjectionAdminEventStore.class);
        when(admin.projectionExists(ArgumentMatchers.<ProjectionId>any())).thenReturn(false);
        return admin;
    }

    private static AuthorizationProjectionRunner runner(final EventStore eventStore,
                                                        final ProjectionAdminEventStore admin,
                                                        final Duration poll, final Duration maxStaleness) {
        return runner(eventStore, admin, poll, maxStaleness, false);
    }

    private static AuthorizationProjectionRunner runner(final EventStore eventStore,
                                                        final ProjectionAdminEventStore admin,
                                                        final Duration poll, final Duration maxStaleness,
                                                        final boolean multitenancy) {
        final AuthorizationView view = new AuthorizationView(List.of(new NoopSource()), new PermissionState(),
                "*/5 * * * * *", T);
        return new AuthorizationProjectionRunner(eventStore, admin, view, T, poll, maxStaleness, multitenancy);
    }

    private static final class NoopSource implements PermissionEventSource {

        @Override
        public Set<EventType> eventTypes() {
            return Set.of(GRANTED);
        }

        @Override
        public void apply(final TenantId tenantId, final Event event, final PermissionState state) {
            // Nothing - this test is about the runner, not the fold.
        }

    }

}
