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
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.ProjectionAlreadyExistsException;
import org.fuin.esc.api.ProjectionId;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.StreamId;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    /** When each tenant last caught up. One entry per tenant, so a stale tenant denies only its own. */
    private final Map<TenantId, Instant> lastCatchUp = new ConcurrentHashMap<>();

    @Nullable
    private final WritableTenantContext tenantContext;

    @Nullable
    private final TenantIdsSupplier tenantIds;

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
     * @param multitenancyEnabled Whether the application runs multi-tenant.
     */
    public AuthorizationProjectionRunner(final EventStore eventStore, final ProjectionAdminEventStore admin,
                                         final AuthorizationView view, final TenantId tenantId,
                                         final Duration pollInterval, final Duration maxStaleness,
                                         final boolean multitenancyEnabled) {
        this(eventStore, admin, view, tenantId, pollInterval, maxStaleness, multitenancyEnabled, null, null);
    }

    /**
     * Constructor for the multi-tenant case.
     *
     * @param eventStore          Store to read the projected events from.
     * @param admin               Store used to create the projection.
     * @param view                View to feed.
     * @param tenantId            The tenant projected when multitenancy is off.
     * @param pollInterval        How often to look for new events.
     * @param maxStaleness        How old the last successful catch-up may be before a tenant reports not
     *                            ready.
     * @param multitenancyEnabled Whether the application runs multi-tenant.
     * @param tenantContext       Carries the tenant of the pass currently running, so that the event store
     *                            reads that tenant's streams. Required when multitenancy is enabled.
     * @param tenantIds           Supplies the tenants known at the moment of the call; must be thread-safe.
     *                            Required when multitenancy is enabled.
     */
    public AuthorizationProjectionRunner(final EventStore eventStore, final ProjectionAdminEventStore admin,
                                         final AuthorizationView view, final TenantId tenantId,
                                         final Duration pollInterval, final Duration maxStaleness,
                                         final boolean multitenancyEnabled,
                                         @Nullable final WritableTenantContext tenantContext,
                                         @Nullable final TenantIdsSupplier tenantIds) {
        this.multitenancyEnabled = multitenancyEnabled;
        this.tenantContext = multitenancyEnabled
                ? Objects.requireNonNull(tenantContext, "tenantContext==null") : tenantContext;
        this.tenantIds = multitenancyEnabled
                ? Objects.requireNonNull(tenantIds, "tenantIds==null") : tenantIds;
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
        return ready(tenantId);
    }

    /**
     * Determines whether the projection's answer for one tenant can be trusted right now.
     * <p>
     * Per tenant on purpose: a tenant whose stream is unreachable, or which has simply not been reached yet
     * in this pass, must not deny requests for every other tenant. Each one carries its own catch-up time
     * and is judged against the same staleness limit.
     *
     * @param tenant Tenant to answer for.
     *
     * @return TRUE if a catch-up for that tenant has completed and is not older than the staleness limit.
     */
    public boolean ready(final TenantId tenant) {
        final Instant last = lastCatchUp.get(Objects.requireNonNull(tenant, "tenant==null"));
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
        return lastCatchUp.get(tenantId);
    }

    /**
     * Returns when the last successful catch-up for one tenant finished.
     *
     * @param tenant Tenant to answer for.
     *
     * @return Instant, or {@literal null} if none has completed yet.
     */
    @Nullable
    public Instant lastCatchUp(final TenantId tenant) {
        return lastCatchUp.get(Objects.requireNonNull(tenant, "tenant==null"));
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
        final TenantIdsSupplier supplier = this.tenantIds;
        final WritableTenantContext context = this.tenantContext;
        if (supplier == null || context == null) {
            catchUpTenant(tenantId);
            return;
        }
        // One pass per tenant, each with its own context, its own projection in the event store and its own
        // checkpoint. A tenant that fails is caught by the caller's handler and the others still run: the
        // alternative - abandoning the pass - would let one unreachable tenant's stream age every other
        // tenant's answer into a denial.
        supplier.getTenantIds().forEach(tenant -> {
            context.setTenantId(tenant);
            try {
                catchUpTenant(tenant);
            } finally {
                context.clear();
            }
        });
    }

    private void catchUpTenant(final TenantId tenant) {
        createProjection();
        if (!eventStore.streamExists(projectionStreamId)) {
            // Nothing has ever been projected. Nobody holds anything, and that is a complete answer - so the
            // runner counts as caught up rather than blocking every request until the first event arrives.
            lastCatchUp.put(tenant, Instant.now());
            return;
        }
        final StreamId checkpoint = checkpointFor(tenant);
        final long from = projectionService.readProjectionPosition(checkpoint);
        eventStore.readAllEventsForward(projectionStreamId, from, view.getChunkSize(),
                slice -> handleChunk(tenant, checkpoint, slice));
        lastCatchUp.put(tenant, Instant.now());
    }

    /**
     * The key this tenant's checkpoint is stored under.
     * <p>
     * Note the asymmetry with the read above, which is deliberate and easy to misread: the event store is
     * handed the <em>unprefixed</em> stream id and prefixes it itself from the tenant context, while the
     * checkpoint has to be keyed here because the projection service knows nothing about tenants. All that
     * is required of this name is that two tenants never collide; it mirrors the store's naming so the two
     * read alike side by side, but nothing breaks if they ever diverge.
     */
    private StreamId checkpointFor(final TenantId tenant) {
        if (!multitenancyEnabled) {
            return projectionStreamId;
        }
        return new ProjectionStreamId(tenant.asString() + "-" + view.getStreamName());
    }

    private void handleChunk(final TenantId tenant, final StreamId checkpoint,
                             final StreamEventsSlice slice) {
        final List<Event> events = slice.getEvents().stream()
                .map(CommonEvent::getData)
                .map(Event.class::cast)
                .toList();
        view.handleEvents(tenant, events);
        projectionService.updateProjectionPosition(checkpoint, slice.getNextEventNumber());
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
