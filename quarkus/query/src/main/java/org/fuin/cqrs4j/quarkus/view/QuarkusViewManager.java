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
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    @Inject
    Instance<WritableTenantContext> tenantContextInstance;

    @Inject
    Instance<TenantIdsSupplier> tenantIdsSupplierInstance;

    @Inject
    Instance<SubscribableEventStoreAsync> subscribableEventStoreInstance;

    private static final long RESUBSCRIBE_BACKOFF_MILLIS = 5000L;

    private final String instanceId = UUID.randomUUID().toString();

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
            // Read and dispatch events. A failure here is a real processing error (e.g. a view handler throwing).
            eventstore.readAllEventsForward(projectionStreamId, nextEventNumber, viewJob.getEntry().chunkSize(),
                    currentSlice -> handleChunk(viewJob, currentSlice));
        } catch (final RuntimeException ex) {
            LOG.error("Error processing events for viewJob '" + viewJob.entry.beanName() + "'", ex);
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
            createProjection(viewJob);
            return projectionService.readProjectionPosition(projectionStreamId);
        } catch (final RuntimeException ex) {
            if (isTransientInfrastructureFailure(ex)) {
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
     * Determines if the given error looks like a transient event store / infrastructure connectivity failure
     * (gRPC transport error, socket/IO error, or a JDBC/JPA connection problem) that is expected to self-heal,
     * as opposed to an unexpected programming/configuration error that should be surfaced (e.g. a missing CDI
     * context). Walks the whole cause chain.
     *
     * @param error Error to classify.
     * @return {@literal true} if the error is a transient infrastructure failure.
     */
    private static boolean isTransientInfrastructureFailure(final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof java.io.IOException) {
                return true;
            }
            final String type = t.getClass().getName();
            if (type.startsWith("io.grpc.")                       // gRPC transport / StatusRuntimeException
                    || type.startsWith("java.net.")               // ConnectException, SocketException, ...
                    || type.startsWith("java.sql.")               // SQLException / transient DB errors
                    || type.startsWith("jakarta.persistence.")    // JPA persistence exceptions on a DB hiccup
                    || type.startsWith("org.springframework.dao.")) { // Spring's DataAccessException hierarchy
                return true;
            }
        }
        return false;
    }

    private void createProjection(@NotNull final ViewExt viewJob) {
        if (!admin.projectionExists(viewJob.getProjectionId())) {
            final List<TypeName> typeNames = asTypeNames(viewJob.getEntry().eventTypes());
            try {
                admin.createProjection(viewJob.getProjectionId(), viewJob.getProjectionStreamId(), true, typeNames);
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
            final String checksumPostfix = "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes());
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
