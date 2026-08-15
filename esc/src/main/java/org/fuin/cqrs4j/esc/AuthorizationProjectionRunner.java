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
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.ProjectionAlreadyExistsException;
import org.fuin.esc.api.ProjectionId;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.StreamAlreadyExistsException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.TypeName;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps an {@link AuthorizationView} up to date by polling the event store.
 * <p>
 * Deliberately <b>not</b> a generalization of the query side's view manager. That class carries leases,
 * a circuit breaker, push subscriptions, transactions and multitenancy, all of which exist for projections
 * that write to a shared database. This one feeds a per-process in-memory structure, where none of that
 * applies: every instance maintains its own copy, so there is nothing to coordinate and nothing to commit.
 * <p>
 * <b>It fails closed.</b> {@link #ready()} is false until the first catch-up completes, and false again if
 * the last one is older than the configured staleness limit. An authorizer asking a lookup backed by this
 * runner therefore denies during startup and denies again if the event store becomes unreachable - rather
 * than serving a snapshot that stopped advancing, which would keep honoring a permission that has since
 * been revoked.
 * <p>
 * Startup never blocks on the event store. A process whose store is down comes up, answers every request
 * with a denial, and starts working the moment the store returns.
 * <p>
 * <b>Single-tenant only, and it says so.</b> Under multitenancy each tenant has its own stream and needs its
 * own checkpoint, which means the loop below has to run once per known tenant with that tenant's context
 * set - what the query side's view manager does. Until that exists, {@link #start()} throws rather than
 * quietly projecting one tenant and denying every other. The state it feeds is already keyed by tenant, so
 * adding the loop is the only thing missing.
 */
@ThreadSafe
public final class AuthorizationProjectionRunner implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationProjectionRunner.class);

    private final EventStore eventStore;

    private final ProjectionAdminEventStore admin;

    private final AuthorizationView view;

    private final TenantId tenantId;

    private final ProjectionService projectionService;

    private final ProjectionId projectionId;

    private final ProjectionStreamId projectionStreamId;

    private final Duration pollInterval;

    private final Duration maxStaleness;

    private final boolean multitenancyEnabled;

    private final AtomicReference<@Nullable Instant> lastCatchUp = new AtomicReference<>();

    @Nullable
    private ScheduledExecutorService scheduler;

    /**
     * Constructor with everything the runner needs.
     *
     * @param eventStore   Store to read the projected events from.
     * @param admin        Store used to create the projection.
     * @param view         View to feed.
     * @param tenantId            The single tenant this runner projects.
     * @param pollInterval        How often to look for new events.
     * @param maxStaleness        How old the last successful catch-up may be before the runner reports not
     *                            ready.
     * @param multitenancyEnabled Whether the application runs multi-tenant. If it does, {@link #start()}
     *                            refuses - see there for why.
     */
    public AuthorizationProjectionRunner(final EventStore eventStore, final ProjectionAdminEventStore admin,
                                         final AuthorizationView view, final TenantId tenantId,
                                         final Duration pollInterval, final Duration maxStaleness,
                                         final boolean multitenancyEnabled) {
        this.multitenancyEnabled = multitenancyEnabled;
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore==null");
        this.admin = Objects.requireNonNull(admin, "admin==null");
        this.view = Objects.requireNonNull(view, "view==null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId==null");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval==null");
        this.maxStaleness = Objects.requireNonNull(maxStaleness, "maxStaleness==null");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("The argument 'pollInterval' must be positive: " + pollInterval);
        }
        if (maxStaleness.compareTo(pollInterval) < 0) {
            // Otherwise the runner reports "stale" between two perfectly healthy polls and denies everything.
            throw new IllegalArgumentException("The argument 'maxStaleness' (" + maxStaleness
                    + ") must not be shorter than 'pollInterval' (" + pollInterval + ")");
        }
        this.projectionService = new InMemoryProjectionService();
        this.projectionId = new ProjectionId(view.getProjectionName());
        this.projectionStreamId = new ProjectionStreamId(view.getStreamName());
    }

    /**
     * Starts polling. Returns immediately - the first catch-up happens on the scheduler thread, so a slow or
     * unreachable event store delays readiness but never startup.
     *
     * @throws IllegalStateException Multitenancy is enabled, which this runner does not support yet.
     */
    public synchronized void start() {
        if (multitenancyEnabled) {
            // Refuse loudly rather than project one tenant and deny every other. Every link in the tenancy
            // chain in this stack degrades silently to single-tenant when it is missing; an authorization
            // component is the worst possible place to add another one. Supporting it means reading each
            // tenant's stream in turn with its own checkpoint, the way the query side's view manager does.
            throw new IllegalStateException("The authorization projection is not tenant-aware yet. Refusing "
                    + "to start rather than projecting a single tenant and denying every other one. Either "
                    + "disable multitenancy, or teach this runner to iterate the known tenants.");
        }
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "authorization-projection");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::catchUpQuietly, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("Authorization projection started (poll={}, maxStaleness={})", pollInterval, maxStaleness);
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            LOG.info("Authorization projection stopped");
        }
    }

    /**
     * Determines whether the projection's answer can be trusted right now.
     *
     * @return TRUE if a catch-up has completed and is not older than the staleness limit.
     */
    public boolean ready() {
        final Instant last = lastCatchUp.get();
        if (last == null) {
            return false;
        }
        return Duration.between(last, Instant.now()).compareTo(maxStaleness) <= 0;
    }

    /**
     * Returns when the last successful catch-up finished.
     *
     * @return Instant, or {@literal null} if none has completed yet.
     */
    @Nullable
    public Instant lastCatchUp() {
        return lastCatchUp.get();
    }

    private void catchUpQuietly() {
        try {
            catchUp();
        } catch (final RuntimeException ex) {
            // Never let an exception escape: scheduleWithFixedDelay cancels the task permanently if one does,
            // and a projection that silently stopped would keep answering from an ever-staler snapshot. The
            // staleness limit turns that into denials, but only if this thread keeps running to be late.
            if (EscUtils.isTransientInfrastructureFailure(ex)) {
                LOG.debug("Could not reach the event store for the authorization projection "
                        + "(will retry in {}): {}", pollInterval, ex.toString());
            } else {
                LOG.error("Error updating the authorization projection (will retry in {})", pollInterval, ex);
            }
        }
    }

    private void catchUp() {
        createProjection();
        if (!eventStore.streamExists(projectionStreamId)) {
            // Nothing has ever been projected. Nobody holds anything, and that is a complete answer - so the
            // runner counts as caught up rather than blocking every request until the first event arrives.
            lastCatchUp.set(Instant.now());
            return;
        }
        final long from = projectionService.readProjectionPosition(projectionStreamId);
        eventStore.readAllEventsForward(projectionStreamId, from, view.getChunkSize(), this::handleChunk);
        lastCatchUp.set(Instant.now());
    }

    private void handleChunk(final StreamEventsSlice slice) {
        final List<Event> events = slice.getEvents().stream()
                .map(CommonEvent::getData)
                .map(Event.class::cast)
                .toList();
        view.handleEvents(tenantId, events);
        projectionService.updateProjectionPosition(projectionStreamId, slice.getNextEventNumber());
    }

    private void createProjection() {
        if (admin.projectionExists(projectionId)) {
            return;
        }
        final List<TypeName> typeNames = asTypeNames(view.getEventTypes());
        // The same conversion SimpleViewRegistry does: a category is named by its marker interface's simple
        // name, not by its class object.
        final List<String> categoryNames = view.getEventCategories().stream().map(Class::getSimpleName).toList();
        LOG.debug("Creating projection: {} (types={})", projectionId, typeNames);
        try {
            admin.createProjection(projectionId, projectionStreamId, true, typeNames, categoryNames);
        } catch (final ProjectionAlreadyExistsException | StreamAlreadyExistsException ex) {
            // Another process created it between the check and the call. It is there either way.
            LOG.info("Race condition: the authorization projection already existed by the time it was "
                    + "created: {}", projectionId);
        }
    }

    private static List<TypeName> asTypeNames(final Set<EventType> eventTypes) {
        return eventTypes.stream().map(eventType -> new TypeName(eventType.asString())).toList();
    }

}
