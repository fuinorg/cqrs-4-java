package org.fuin.cqrs4j.springboot.query.core.view;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    private volatile List<ViewJob> viewJobs = Collections.emptyList();

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
            @Nullable final TenantIdsSupplier tenantIdsSupplier) {
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
    }


    private void readTenantsStreamEvents(@NotNull final ViewJob viewJob) {
        final TenantIdsSupplier supplier = this.tenantIdsSupplier;
        final WritableTenantContext tc = this.tenantContext;
        if (supplier == null || tc == null) {
            LOG.debug("No tenant supplier found...");
            readStreamEvents(viewJob);
        } else {
            supplier.getTenantIds().forEach(tenantId -> {
                tc.setTenantId(tenantId);
                try {
                    readStreamEvents(viewJob);
                } finally {
                    tc.clear();
                }
            });
        }
    }

    private void readStreamEvents(@NotNull final ViewJob viewJob) {
        try {

            // Create an event store projection if it does not exist.
            createProjection(viewJob);

            // Read and dispatch events
            final ProjectionStreamId projectionStreamId = viewJob.getProjectionStreamId();
            if (eventstore.streamExists(projectionStreamId)) { // May not exist if no events have been projected
                final Long nextEventNumber = projectionService.readProjectionPosition(projectionStreamId);
                eventstore.readAllEventsForward(projectionStreamId, nextEventNumber, viewJob.getEntry().chunkSize(),
                        currentSlice -> handleChunk(viewJob, currentSlice));
            }

        } catch (final RuntimeException ex) {
            LOG.error("Error processing events for viewJob '" + viewJob.entry.beanName() + "'", ex);
        }

    }

    private void createProjection(@NotNull final ViewJob viewJob) {
        if (admin.projectionExists(viewJob.getProjectionId())) {
            LOG.trace("Projection already exists: {}", viewJob.getProjectionId());
        } else {
            final List<TypeName> typeNames = asTypeNames(viewJob.getEntry().eventTypes());
            LOG.debug("Creating projection: {} ({})", viewJob.getProjectionId(), typeNames);
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

    private void handleChunk(final ViewJob viewJob, final StreamEventsSlice currentSlice) {
        LOG.debug("Handle chunk: {}", currentSlice);
        requiresNewTransaction.execute(new TransactionCallbackWithoutResult() {
            public void doInTransactionWithoutResult(TransactionStatus status) {
                LOG.debug("Begin transaction: {}", viewJob.getProjectionStreamId());
                viewJob.handleEvents(beanFactory, asEvents(currentSlice.getEvents()));
                LOG.atDebug().setMessage("Events handled: {}").addArgument(() -> asEventNames(currentSlice.getEvents())).log();
                projectionService.updateProjectionPosition(viewJob.getProjectionStreamId(), currentSlice.getNextEventNumber());
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
            final String checksumPostfix = "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes());
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
