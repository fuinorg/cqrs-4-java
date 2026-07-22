package org.fuin.cqrs4j.springboot.query.core.view;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionLeaseService;
import org.fuin.cqrs4j.esc.EscUtils;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.esc.ViewSubscriptions;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.esc.api.ProjectionId;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.StreamAlreadyExistsException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.TypeName;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Creates scheduler update tasks for all classes implementing the {@link View} interface.
 * Avoids boilerplate code: Instead of having a separated "Projector", "EventDispatcher"
 * and a "ChunkHandler" class for each view, there is only one simplified "View" class now.
 */
@ThreadSafe
public class SpringViewManager implements ApplicationListener<ContextClosedEvent>, SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringViewManager.class);

    /** Number of catch-up attempts the circuit breaker judges before it may open. */
    private static final int BREAKER_WINDOW_SIZE = 4;

    /** Percentage of failed attempts within the window that opens the circuit breaker. */
    private static final float BREAKER_FAILURE_RATE_PERCENT = 50.0f;

    /** Wait before the first probe after the circuit breaker opened. */
    private static final Duration BREAKER_INITIAL_WAIT = Duration.ofSeconds(5);

    /** Factor the wait between probes grows by while the store stays unreachable. */
    private static final double BREAKER_BACKOFF_MULTIPLIER = 2.0;

    /** Upper bound for the wait between probes, so the view still recovers after a long outage. */
    private static final Duration BREAKER_MAX_WAIT = Duration.ofMinutes(5);

    private final ScheduledAnnotationBeanPostProcessor postProcessor;

    private final ViewRegistry viewRegistry;

    private final EventStore eventstore;

    private final ProjectionAdminEventStore admin;

    private final ProjectionService projectionService;

    private final TransactionTemplate requiresNewTransaction;

    private final ConfigurableBeanFactory beanFactory;

    @Nullable
    private final WritableTenantContext tenantContext;

    @Nullable
    private final TenantIdsSupplier tenantIdsSupplier;

    private final ProjectionLeaseService leaseService;

    private final boolean haEnabled;

    private final String owner;

    private final long leaseTtlMillis;

    /**
     * Guards the event store / database access of the scheduled catch-up. Without it a wedged store is hit
     * again on every single tick, and each attempt blocks a thread until it times out. Once the breaker
     * opens the attempts fail immediately, and the wait before the next probe grows exponentially so a
     * longer outage is not hammered. Only transient infrastructure failures count as a failure, so a broken
     * view handler does not open it.
     */
    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("cqrs4j-projection-catch-up",
            CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(BREAKER_WINDOW_SIZE)
                    .minimumNumberOfCalls(BREAKER_WINDOW_SIZE)
                    .failureRateThreshold(BREAKER_FAILURE_RATE_PERCENT)
                    .waitIntervalFunctionInOpenState(IntervalFunction.ofExponentialBackoff(
                            BREAKER_INITIAL_WAIT, BREAKER_BACKOFF_MULTIPLIER, BREAKER_MAX_WAIT))
                    .recordException(EscUtils::isTransientInfrastructureFailure)
                    .build());

    private final boolean multitenancyEnabled;

    @Nullable
    private final SubscribableEventStoreAsync subscribableEventStore;

    private final boolean pushEnabled;

    @Nullable
    private ScheduledExecutorService resubscribeScheduler;

    @Nullable
    private ViewSubscriptions viewSubscriptions;

    private volatile List<ViewJob> viewJobs = Collections.emptyList();

    private static final long RESUBSCRIBE_BACKOFF_MILLIS = 5000L;

    /**
     * Constructor with mandatory data.
     *
     * @param postProcessor       Helps to cancel the scheduled jobs ob shutdown.
     * @param viewRegistry        List with user defined view classes.
     * @param eventstore          Eventstore instance to use.
     * @param admin               Admin interface to eventstore.
     * @param projectionService   Service to manage projections.
     * @param transactionManager  Helps to open the necessary transactions manually.
     * @param beanFactory         Bean factory.
     * @param multitenancyEnabled Determines if multitenancy is enabled or nit.
     * @param tenantContext       Tenant context.
     * @param tenantIdsSupplier   Supplies the tenant identifiers know at the moment of the call. Required to be thread-safe!
     * @param leaseService        Distributed projection lease service.
     * @param haEnabled           Determines if the distributed lease (multi-instance safe projections) is enabled.
     * @param owner               Identifier of this application instance (lease owner).
     * @param leaseTtlMillis      Time-to-live of an acquired lease in milliseconds.
     * @param subscribableEventStore Subscribable event store used for the low-latency push mode (may be
     *                            {@literal null} if the application does not provide one).
     * @param pushEnabled         Determines if the low-latency push mode is requested.
     */
    public SpringViewManager(
            final ScheduledAnnotationBeanPostProcessor postProcessor,
            final ViewRegistry viewRegistry,
            final EventStore eventstore,
            final ProjectionAdminEventStore admin,
            final ProjectionService projectionService,
            final PlatformTransactionManager transactionManager,
            final ConfigurableBeanFactory beanFactory,
            final boolean multitenancyEnabled,
            @Nullable final WritableTenantContext tenantContext,
            @Nullable final TenantIdsSupplier tenantIdsSupplier,
            final ProjectionLeaseService leaseService,
            final boolean haEnabled,
            final String owner,
            final long leaseTtlMillis,
            @Nullable final SubscribableEventStoreAsync subscribableEventStore,
            final boolean pushEnabled) {
        this.postProcessor = Objects.requireNonNull(postProcessor, "postProcessor==null");
        this.viewRegistry = Objects.requireNonNull(viewRegistry, "viewClassRegistry==null");
        this.eventstore = Objects.requireNonNull(eventstore, "eventstore==null");
        this.admin = Objects.requireNonNull(admin, "admin==null");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService==null");
        Objects.requireNonNull(transactionManager, "transactionManager==null");
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTransaction.setTimeout(10);
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory==null");
        this.tenantContext = multitenancyEnabled ? Objects.requireNonNull(tenantContext, "tenantContext==null") : null;
        this.tenantIdsSupplier = multitenancyEnabled ? Objects.requireNonNull(tenantIdsSupplier, "tenantIdsSupplier==null") : null;
        this.leaseService = Objects.requireNonNull(leaseService, "leaseService==null");
        this.haEnabled = haEnabled;
        this.owner = Objects.requireNonNull(owner, "owner==null");
        this.leaseTtlMillis = leaseTtlMillis;
        this.multitenancyEnabled = multitenancyEnabled;
        this.subscribableEventStore = subscribableEventStore;
        this.pushEnabled = pushEnabled;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        createViews(taskRegistrar);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        shutdownViews();
    }

    private void createViews(ScheduledTaskRegistrar taskRegistrar) {
        LOG.info("Create {} view jobs...", viewRegistry.size());
        if (viewRegistry.isEmpty()) {
            viewJobs = Collections.emptyList();
        } else {
            viewJobs = viewRegistry.getViews().stream()
                    .map(ViewJob::new)
                    .toList();
            for (final ViewJob view : viewJobs) {
                LOG.info("Create view: {}", view.getEntry().name());
                view.setCronTask(new CronTask(() -> tryLocked(view, () -> readTenantsStreamEvents(view)), view.getEntry().cron()));
                taskRegistrar.addCronTask(view.getCronTask());
            }
        }
        startPushSubscriptions();
    }

    /**
     * When the low-latency push mode is enabled (and available), opens a "wake-up" subscription per view: a new
     * event triggers the same guarded catch-up pass the cron would, only sooner. The cron remains as a safety
     * net. Push is only used single-tenant; with multitenancy enabled it falls back to the cron poll.
     */
    private void startPushSubscriptions() {
        final SubscribableEventStoreAsync store = subscribableEventStore;
        if (!pushEnabled) {
            return;
        }
        if (store == null) {
            LOG.warn("Projection push mode requested but no subscribable event store is available; using poll only.");
            return;
        }
        if (multitenancyEnabled) {
            LOG.warn("Projection push mode is not supported with multitenancy; using poll only.");
            return;
        }
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "view-resubscribe");
            thread.setDaemon(true);
            return thread;
        });
        this.resubscribeScheduler = scheduler;
        final ViewSubscriptions subscriptions = new ViewSubscriptions(store, scheduler, RESUBSCRIBE_BACKOFF_MILLIS);
        this.viewSubscriptions = subscriptions;
        for (final ViewJob view : viewJobs) {
            subscriptions.subscribe(view.getProjectionStreamId(), () -> tryLocked(view, () -> readTenantsStreamEvents(view)));
        }
        LOG.info("Projection push mode enabled ({} subscription(s))", viewJobs.size());
    }

    private void shutdownViews() {
        LOG.info("Shutdown {} view jobs...", viewJobs.size());
        final Set<ScheduledTask> scheduledTasks = postProcessor.getScheduledTasks();
        for (final ViewJob viewJob : viewJobs) {
            LOG.info("Shutdown job for view: {}", viewJob.getEntry().name());
            scheduledTasks.stream()
                    .filter(scheduled -> scheduled.getTask() == viewJob.getCronTask())
                    .findFirst()
                    .ifPresent(ScheduledTask::cancel);
        }
        final ViewSubscriptions subscriptions = this.viewSubscriptions;
        if (subscriptions != null) {
            subscriptions.close();
        }
        final ScheduledExecutorService scheduler = this.resubscribeScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }


    private void readTenantsStreamEvents(@NotNull final ViewJob viewJob) {
        final TenantIdsSupplier supplier = this.tenantIdsSupplier;
        final WritableTenantContext tc = this.tenantContext;
        if (supplier == null || tc == null) {
            LOG.debug("No tenant supplier found...");
            readStreamEventsLeased(viewJob);
        } else {
            supplier.getTenantIds().forEach(tenantId -> {
                tc.setTenantId(tenantId);
                try {
                    readStreamEventsLeased(viewJob);
                } finally {
                    tc.clear();
                }
            });
        }
    }

    private void readStreamEventsLeased(@NotNull final ViewJob viewJob) {
        if (!haEnabled) {
            readStreamEvents(viewJob);
            return;
        }
        // Multi-instance safe: only the instance holding the lease processes the projection.
        final ProjectionStreamId projectionStreamId = viewJob.getProjectionStreamId();
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

    private void readStreamEvents(@NotNull final ViewJob viewJob) {
        final ProjectionStreamId projectionStreamId = viewJob.getProjectionStreamId();
        final Long nextEventNumber = prepareRead(viewJob, projectionStreamId);
        if (nextEventNumber == null) {
            // Event store unreachable (already logged) or no events projected yet - nothing to read.
            return;
        }
        try {
            // Read and dispatch events. Shares the breaker with prepareRead: it is the same store and the
            // same database, so a failure in either place counts towards the same breaker.
            circuitBreaker.executeCallable(() -> {
                eventstore.readAllEventsForward(projectionStreamId, nextEventNumber, viewJob.getEntry().chunkSize(),
                        currentSlice -> handleChunk(viewJob, currentSlice));
                return null;
            });
        } catch (final CallNotPermittedException ex) {
            LOG.debug("Circuit breaker is open, skipping the event read for viewJob '{}'",
                    viewJob.entry.beanName());
        } catch (final Exception ex) { // NOSONAR - the guarded call declares Exception
            if (EscUtils.isTransientInfrastructureFailure(ex)) {
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
     * Returns the next event number, or {@literal null} if the projection stream does not exist yet or the
     * event store could not be reached. A failure to reach the store is typically a transient connection drop
     * (reconnect/shutdown) that self-heals on the next scheduled run, so it is logged at debug level rather
     * than as an error.
     *
     * @param viewJob            View job to prepare.
     * @param projectionStreamId Stream to read.
     * @return Next event number, or {@literal null} if nothing should be read this run.
     */
    @Nullable
    private Long prepareRead(final ViewJob viewJob, final ProjectionStreamId projectionStreamId) {
        try {
            return circuitBreaker.executeCallable(() -> {
                createProjection(viewJob);
                if (!eventstore.streamExists(projectionStreamId)) { // May not exist if no events have been projected
                    return null;
                }
                return projectionService.readProjectionPosition(projectionStreamId);
            });
        } catch (final CallNotPermittedException ex) {
            // The store is known to be unreachable: fail immediately instead of blocking a thread on every
            // tick. The wait between probes grows with every failed attempt.
            LOG.debug("Circuit breaker is open, skipping the projection read for viewJob '{}'",
                    viewJob.entry.beanName());
            return null;
        } catch (final Exception ex) { // NOSONAR - the guarded call declares Exception
            if (EscUtils.isTransientInfrastructureFailure(ex)) {
                // Expected during an event store / database reconnect or shutdown; self-heals next run.
                LOG.debug("Could not reach the event store for viewJob '{}' (will retry on the next run): {}",
                        viewJob.entry.beanName(), ex.toString());
            } else {
                // Unexpected (e.g. a transaction, configuration or programming error). Still retry, but make
                // it visible instead of hiding it behind a "cannot reach the store" debug line.
                LOG.error("Unexpected error preparing the projection read for viewJob '{}' (will retry on the next run)",
                        viewJob.entry.beanName(), ex);
            }
            return null;
        }
    }


    private void createProjection(@NotNull final ViewJob viewJob) {
        if (admin.projectionExists(viewJob.getProjectionId())) {
            LOG.trace("Projection already exists: {}", viewJob.getProjectionId());
        } else {
            final List<TypeName> typeNames = asTypeNames(viewJob.getEntry().eventTypes());
            final List<String> categoryNames = List.copyOf(viewJob.getEntry().eventCategories());
            LOG.debug("Creating projection: {} (types={}, categories={})", viewJob.getProjectionId(), typeNames, categoryNames);
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

    private void handleChunk(final ViewJob viewJob, final StreamEventsSlice currentSlice) {
        LOG.debug("Handle chunk: {}", currentSlice);
        requiresNewTransaction.execute(new TransactionCallbackWithoutResult() {
            public void doInTransactionWithoutResult(TransactionStatus status) {
                LOG.debug("Begin transaction: {}", viewJob.getProjectionStreamId());
                viewJob.handleEvents(beanFactory, asEvents(currentSlice.getEvents()));
                LOG.atDebug().setMessage("Events handled: {}").addArgument(() -> asEventNames(currentSlice.getEvents())).log();
                projectionService.updateProjectionPosition(viewJob.getProjectionStreamId(), currentSlice.getNextEventNumber());
                if (haEnabled) {
                    // Keep the lease alive during a long catch-up (commits with the checkpoint).
                    leaseService.renew(viewJob.getProjectionStreamId(), owner, leaseTtlMillis);
                }
                LOG.debug("End transaction: {} (NextEventNumber={})", viewJob.getProjectionStreamId(), currentSlice.getNextEventNumber());
            }
        });
    }

    private static List<Event> asEvents(List<CommonEvent> events) {
        return events.stream().map(event -> (Event) event.getData()).toList();
    }

    private static List<String> asEventNames(List<CommonEvent> events) {
        return events.stream().map(CommonEvent::getDataType).map(TypeName::asBaseType).toList();
    }

    private static void tryLocked(final ViewJob viewJob, final Runnable code) {
        Objects.requireNonNull(viewJob, "view==null");
        Objects.requireNonNull(code, "code==null");
        new Thread(() -> {
            final String name = viewJob.getEntry().name();
            final Lock lock = viewJob.getLock();
            if (lock.tryLock()) {
                LOG.trace("Locked acquired: {} ({})", name, viewJob.hashCode());
                try {
                    code.run();
                } finally {
                    lock.unlock();
                    LOG.trace("Lock released: {} ({})", name, viewJob.hashCode());
                }
            } else {
                LOG.trace("Lock missed: {} ({})", name, viewJob.hashCode());
            }
        }).start();
    }


    /**
     * Extends the view with some necessary values used only by this class.
     */
    private static class ViewJob {

        private final ViewRegistry.Entry entry;

        private final ProjectionId projectionId;

        private final ProjectionStreamId projectionStreamId;

        private final Lock lock;

        @Nullable
        private CronTask cronTask;

        public ViewJob(final ViewRegistry.Entry entry) {
            this.entry = Objects.requireNonNull(entry, "entry==null");
            final String checksumPostfix = "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes(), entry.eventCategories());
            projectionId = new ProjectionId(entry.projectionName() + checksumPostfix);
            projectionStreamId = new ProjectionStreamId(entry.streamName() + checksumPostfix);
            this.lock = new ReentrantLock(true);
        }

        /**
         * Returns the task used.
         *
         * @return Task.
         */
        @Nullable
        public CronTask getCronTask() {
            return cronTask;
        }

        /**
         * Sets the task to use.
         *
         * @param cronTask Task.
         */
        public void setCronTask(CronTask cronTask) {
            this.cronTask = cronTask;
        }

        public ViewRegistry.Entry getEntry() {
            return entry;
        }

        public void handleEvents(final ConfigurableBeanFactory beanFactory,
                                 final List<Event> events) {
            LOG.debug("Creating view bean: {}", entry.beanName());
            final View view = beanFactory.getBean(entry.beanName(), entry.viewClass());
            try {
                view.handleEvents(events);
            } finally {
                beanFactory.destroyBean(entry.beanName(), view);
                LOG.debug("Destroyed view bean: {}", view.getBeanName());
            }
        }

        public ProjectionId getProjectionId() {
            return projectionId;
        }

        public ProjectionStreamId getProjectionStreamId() {
            return projectionStreamId;
        }

        public Lock getLock() {
            return lock;
        }

    }

}
