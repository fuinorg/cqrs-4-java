package org.fuin.cqrs4j.quarkus.view;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import io.smallrye.faulttolerance.api.Guard;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionLeaseService;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.esc.ViewSubscriptions;
import org.fuin.cqrs4j.quarkus.base.QuarkusUtils;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.ProjectionId;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.StreamAlreadyExistsException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.esc.api.TypeName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import static org.fuin.utils4j.Utils4J.tryLocked;

/**
 * Creates scheduler update jobs for all classes implementing the {@link View} interface.
 * Avoids boilerplate code: Instead of having a separated "Projector", "EventDispatcher"
 * and a "ChunkHandler" class for each view, there is only one simplified "View" class now.
 */
@ThreadSafe
@ApplicationScoped
public class QuarkusViewManager {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusViewManager.class);

    @Inject
    Scheduler scheduler;

    @Inject
    ViewRegistry viewRegistry;

    @Inject
    EventStore eventstore;

    @Inject
    ProjectionAdminEventStore admin;

    @Inject
    ProjectionService projectionService;

    @Inject
    ProjectionLeaseService leaseService;

    @Inject
    BeanManager beanManager;

    @ConfigProperty(name = "org.fuin.cqrs4j.multitenancy")
    boolean multitenancy;

    @ConfigProperty(name = "org.fuin.cqrs4j.projection.ha.enabled", defaultValue = "false")
    boolean haEnabled;

    @ConfigProperty(name = "org.fuin.cqrs4j.projection.ha.ttl", defaultValue = "60000")
    long leaseTtlMillis;

    @ConfigProperty(name = "org.fuin.cqrs4j.projection.ha.owner", defaultValue = "")
    String ownerProperty = "";

    @ConfigProperty(name = "org.fuin.cqrs4j.projection.mode", defaultValue = "poll")
    String projectionMode = "poll";

    @ConfigProperty(name = "org.fuin.cqrs4j.projection.breaker.delay", defaultValue = "30000")
    long breakerDelayMillis;

    @ConfigProperty(name = "org.fuin.cqrs4j.projection.breaker.requestVolumeThreshold", defaultValue = "4")
    int breakerRequestVolumeThreshold;

    @ConfigProperty(name = "org.fuin.cqrs4j.projection.breaker.failureRatio", defaultValue = "0.5")
    double breakerFailureRatio;

    @Inject
    Instance<WritableTenantContext> tenantContextInstance;

    @Inject
    Instance<TenantIdsSupplier> tenantIdsSupplierInstance;

    @Inject
    Instance<SubscribableEventStoreAsync> subscribableEventStoreInstance;

    private static final long RESUBSCRIBE_BACKOFF_MILLIS = 5000L;

    private final String instanceId = UUID.randomUUID().toString();

    /**
     * Guards the event store / database access of the scheduled catch-up. Without it a wedged store is
     * hit again on every single tick, and each attempt blocks a thread until it times out. Once the
     * breaker opens, the attempts fail immediately until the delay elapsed and the store is probed again.
     * Only transient infrastructure failures count as a failure, so a broken view handler does not open it.
     */
    @Nullable
    private volatile Guard catchUpGuard;

    private volatile List<ViewExt> views = Collections.emptyList();

    @Nullable
    private ScheduledExecutorService resubscribeScheduler;

    @Nullable
    private ViewSubscriptions viewSubscriptions;

    private String owner() {
        return ownerProperty.isBlank() ? instanceId : ownerProperty;
    }

    @Startup
    void createViews() {
        LOG.info("Create {} views...", viewRegistry.size());
        if (viewRegistry.isEmpty()) {
            views = Collections.emptyList();
        } else {
            views = viewRegistry.getViews().stream()
                    .map(ViewExt::new)
                    .toList();
            for (final ViewExt view : views) {
                LOG.info("Create view: {}", view.getEntry().viewClass().getSimpleName());
                scheduler.newJob(view.getEntry().beanName())
                        .setCron(view.getEntry().cron())
                        .setTask(executionContext -> updateView(view))
                        .schedule();
            }
        }
        startPushSubscriptions();
    }

    /**
     * Opens "wake-up" subscriptions when push mode is effective, so a new event triggers the catch-up pass
     * immediately instead of waiting for the next cron tick. The cron poll stays active as a safety net. Push is
     * effective only when the mode is {@code push}, a {@link SubscribableEventStoreAsync} is available, and
     * multitenancy is off; otherwise a one-time warning is logged and the manager stays on the poll.
     */
    private void startPushSubscriptions() {
        if (!"push".equalsIgnoreCase(projectionMode)) {
            return;
        }
        if (subscribableEventStoreInstance.isUnsatisfied()) {
            LOG.warn("Projection push mode requested but no subscribable event store is available; using poll only.");
            return;
        }
        if (multitenancy) {
            LOG.warn("Projection push mode is not supported with multitenancy; using poll only.");
            return;
        }
        final SubscribableEventStoreAsync store = subscribableEventStoreInstance.get();
        final ScheduledExecutorService schedulerService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "view-resubscribe");
            thread.setDaemon(true);
            return thread;
        });
        this.resubscribeScheduler = schedulerService;
        final ViewSubscriptions subscriptions = new ViewSubscriptions(store, schedulerService, RESUBSCRIBE_BACKOFF_MILLIS);
        this.viewSubscriptions = subscriptions;
        for (final ViewExt view : views) {
            subscriptions.subscribe(view.getProjectionStreamId(), () -> updateView(view));
        }
        LOG.info("Projection push mode enabled ({} subscription(s))", views.size());
    }

    @Shutdown
    void shutdownViews() {
        LOG.info("Shutdown views...");
        for (final ViewExt view : views) {
            LOG.info("Shutdown: {}", view.getEntry().viewClass().getSimpleName());
            scheduler.unscheduleJob(view.getEntry().beanName());
        }
        final ViewSubscriptions subscriptions = this.viewSubscriptions;
        if (subscriptions != null) {
            subscriptions.close();
        }
        final ScheduledExecutorService schedulerService = this.resubscribeScheduler;
        if (schedulerService != null) {
            schedulerService.shutdownNow();
        }
    }

    private void updateView(final ViewExt viewJob) {
        tryLocked(viewJob.getLock(), () -> new Thread(() -> {
            LOG.debug("updateView({})", viewJob.getEntry().viewClass().getName());
            // The catch-up pass runs on a plain worker thread that has no CDI request context active.
            // Activate a request context for the duration of the pass so the JPA EntityManager is
            // usable (the write in handleChunk additionally opens its own transaction).
            final ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                readTenantsStreamEvents(viewJob);
            } finally {
                requestContext.terminate();
            }
        }
        ).start());
    }

    private void readTenantsStreamEvents(@NotNull final ViewExt viewJob) {
        if (multitenancy) {
            final WritableTenantContext tenantContext = tenantContextInstance.get();
            final TenantIdsSupplier tenantIdsSupplier = tenantIdsSupplierInstance.get();
            tenantIdsSupplier.getTenantIds().forEach(tenantId -> {
                tenantContext.setTenantId(tenantId);
                try {
                    readStreamEventsLeased(viewJob);
                } finally {
                    tenantContext.clear();
                }
            });
        } else {
            readStreamEventsLeased(viewJob);
        }
    }

    private void readStreamEventsLeased(@NotNull final ViewExt viewJob) {
        if (!haEnabled) {
            readStreamEvents(viewJob);
            return;
        }
        // Multi-instance safe: only the instance holding the lease processes the projection.
        final ProjectionStreamId projectionStreamId = viewJob.getProjectionStreamId();
        final String owner = owner();
        if (leaseService.acquire(projectionStreamId, owner, leaseTtlMillis)) {
            try {
                readStreamEvents(viewJob);
            } finally {
                leaseService.release(projectionStreamId, owner);
            }
        } else {
            LOG.trace("Projection lease held by another instance, skipping: {}", projectionStreamId);
        }
    }

    private void readStreamEvents(@NotNull final ViewExt viewJob) {
        final ProjectionStreamId projectionStreamId = viewJob.getProjectionStreamId();
        final Long nextEventNumber = prepareRead(viewJob, projectionStreamId);
        if (nextEventNumber == null) {
            // Event store unreachable (already logged) - nothing to read this run.
            return;
        }
        try {
            // Read and dispatch events. Shares the guard with prepareRead: it is the same store and the same
            // database, so a failure in either place counts towards the same breaker.
            guard().call(() -> {
                eventstore.readAllEventsForward(projectionStreamId, nextEventNumber, viewJob.getEntry().chunkSize(),
                        currentSlice -> handleChunk(viewJob, currentSlice));
                return null;
            }, Void.class);
        } catch (final CircuitBreakerOpenException ex) {
            LOG.debug("Circuit breaker is open, skipping the event read for viewJob '{}'",
                    viewJob.entry.beanName());
        } catch (final Exception ex) { // NOSONAR - the guarded call declares Exception
            if (CqrsUtils.isTransientInfrastructureFailure(ex)) {
                // The store or database went away mid-read; the next run continues from the last checkpoint.
                LOG.debug("Could not read events for viewJob '{}' (will retry on the next run): {}",
                        viewJob.entry.beanName(), ex.toString());
            } else {
                // A real processing error, e.g. a view handler throwing.
                LOG.error("Error processing events for viewJob '" + viewJob.entry.beanName() + "'", ex);
            }
        }
    }

    /**
     * Reaches the event store to prepare a read (ensures the projection exists and reads the checkpoint).
     * Returns the next event number, or {@literal null} if the event store could not be reached. A failure to
     * reach the store is typically a transient connection drop (reconnect/shutdown) that self-heals on the next
     * scheduled run, so it is logged at debug level rather than as an error.
     *
     * @param viewJob            View job to prepare.
     * @param projectionStreamId Stream to read.
     * @return Next event number, or {@literal null} if nothing should be read this run.
     */
    @Nullable
    private Long prepareRead(final ViewExt viewJob, final ProjectionStreamId projectionStreamId) {
        try {
            return guard().call(() -> {
                createProjection(viewJob);
                return projectionService.readProjectionPosition(projectionStreamId);
            }, Long.class);
        } catch (final CircuitBreakerOpenException ex) {
            // The store is known to be unreachable: fail immediately instead of blocking a thread on every
            // tick. The breaker probes it again once the delay elapsed.
            LOG.debug("Circuit breaker is open, skipping the projection read for viewJob '{}'",
                    viewJob.entry.beanName());
            return null;
        } catch (final Exception ex) { // NOSONAR - the guarded call declares Exception
            if (CqrsUtils.isTransientInfrastructureFailure(ex)) {
                // Expected during an event store / database reconnect or shutdown; self-heals next run.
                LOG.debug("Could not reach the event store for viewJob '{}' (will retry on the next run): {}",
                        viewJob.entry.beanName(), ex.toString());
            } else {
                // Unexpected (e.g. a CDI context, configuration or programming error). Still retry, but make
                // it visible instead of hiding it behind a "cannot reach the store" debug line.
                LOG.error("Unexpected error preparing the projection read for viewJob '{}' (will retry on the next run)",
                        viewJob.entry.beanName(), ex);
            }
            return null;
        }
    }

    /**
     * Returns the guard for the catch-up, created lazily because the configuration is injected after
     * construction.
     *
     * @return Guard shared by all views - it is the event store / database that is unavailable, not a
     *         single view.
     */
    private Guard guard() {
        Guard result = catchUpGuard;
        if (result == null) {
            synchronized (this) {
                result = catchUpGuard;
                if (result == null) {
                    result = Guard.create()
                            .withDescription("cqrs4j-projection-catch-up")
                            .withCircuitBreaker()
                            // A failing view handler or a configuration error must not open the breaker,
                            // only a store/database that cannot be reached.
                            .when(CqrsUtils::isTransientInfrastructureFailure)
                            .requestVolumeThreshold(breakerRequestVolumeThreshold)
                            .failureRatio(breakerFailureRatio)
                            .delay(breakerDelayMillis, ChronoUnit.MILLIS)
                            .onStateChange(state -> LOG.info("Projection catch-up circuit breaker is now {}", state))
                            .done()
                            .build();
                    catchUpGuard = result;
                }
            }
        }
        return result;
    }


    private void createProjection(@NotNull final ViewExt viewJob) {
        if (!admin.projectionExists(viewJob.getProjectionId())) {
            final List<TypeName> typeNames = asTypeNames(viewJob.getEntry().eventTypes());
            final List<String> categoryNames = List.copyOf(viewJob.getEntry().eventCategories());
            try {
                admin.createProjection(viewJob.getProjectionId(), viewJob.getProjectionStreamId(), true, typeNames, categoryNames);
            } catch (StreamAlreadyExistsException ex) {
                LOG.info("Race condition: After checking if project exists, the create failed with 'already exists'");
            }
        }
    }

    private List<TypeName> asTypeNames(Set<EventType> eventTypes) {
        return eventTypes.stream().map(eventType -> new TypeName((eventType.asString()))).toList();
    }

    private void handleChunk(final ViewExt viewExt,
                             final StreamEventsSlice currentSlice) {
        QuarkusTransaction.requiringNew()
                .timeout(10)
                .call(() -> {
                    LOG.debug("Handle chunk: {}", currentSlice);
                    viewExt.handleEvents(beanManager, asEvents(currentSlice.getEvents()));
                    projectionService.updateProjectionPosition(viewExt.getProjectionStreamId(), currentSlice.getNextEventNumber());
                    if (haEnabled) {
                        // Keep the lease alive during a long catch-up (commits with the checkpoint).
                        leaseService.renew(viewExt.getProjectionStreamId(), owner(), leaseTtlMillis);
                    }
                    return 0;
                });
    }

    private List<org.fuin.ddd4j.core.Event> asEvents(List<CommonEvent> events) {
        return events.stream().map(event -> (Event) event.getData()).toList();
    }

    /**
     * Extends the view with some necessary values used only by this class.
     */
    private static class ViewExt {

        private final ViewRegistry.Entry entry;

        private final ProjectionId projectionId;

        private final ProjectionStreamId projectionStreamId;

        private final Semaphore lock;

        public ViewExt(final ViewRegistry.Entry entry) {
            this.entry = Objects.requireNonNull(entry, "entry==null");
            final String checksumPostfix = "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes(), entry.eventCategories());
            projectionId = new ProjectionId(entry.projectionName() + checksumPostfix);
            projectionStreamId = new ProjectionStreamId(entry.streamName() + checksumPostfix);
            this.lock = new Semaphore(1);

        }

        public ViewRegistry.Entry getEntry() {
            return entry;
        }

        public void handleEvents(BeanManager beanManager, List<Event> events) {
            final View view = getView(beanManager, entry);
            view.handleEvents(events);
        }

        public ProjectionId getProjectionId() {
            return projectionId;
        }

        public ProjectionStreamId getProjectionStreamId() {
            return projectionStreamId;
        }

        public Semaphore getLock() {
            return lock;
        }

        private static View getView(BeanManager beanManager, ViewRegistry.Entry entry) {
            final Bean<?> bean = QuarkusUtils.findBean(beanManager, entry.beanName(), entry.viewClass())
                    .orElseThrow(() -> new IllegalStateException("No bean named '" + entry.beanName()
                            + "' of type '" + entry.viewClass().getName() + "' found"));
            final CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
            return (View) beanManager.getReference(bean, entry.viewClass(), ctx);
        }

    }

}
