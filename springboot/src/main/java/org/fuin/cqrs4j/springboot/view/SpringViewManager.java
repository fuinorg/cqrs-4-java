package org.fuin.cqrs4j.springboot.view;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.SimpleTenantId;
import org.fuin.esc.api.StreamAlreadyExistsException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.TenantContext;
import org.fuin.esc.api.TenantStreamId;
import org.fuin.esc.api.TypeName;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static org.fuin.utils4j.Utils4J.tryLocked;

/**
 * Creates scheduler update tasks for all classes implementing the {@link View} interface.
 * Avoids boilerplate code: Instead of having a separated "Projector", "EventDispatcher"
 * and a "ChunkHandler" class for each view, there is only one simplified "View" class now.
 */
public class SpringViewManager implements ApplicationListener<ContextClosedEvent>, SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringViewManager.class);

    private final ScheduledAnnotationBeanPostProcessor postProcessor;

    private final ViewRegistry viewRegistry;

    private final EventStore eventstore;

    private final ProjectionAdminEventStore admin;

    private final ProjectionService projectionService;

    private final TransactionTemplate requiresNewTransaction;

    private final ConfigurableBeanFactory beanFactory;

    private final WritableTenantContext tenantContext;

    private final TenantIdsSupplier tenantIdsSupplier;

    private List<ViewJob> viewJobs;

    /**
     * Constructor with mandatory data.
     *
     * @param postProcessor       Helps to cancel the scheduled jobs ob shutdown.
     * @param viewRegistry        List with user defined view classes.
     * @param eventstore          Eventstore instance to use.
     * @param admin               Admin interface to eventstore.
     * @param projectionService   Service to manage projections.
     * @param transactionManager  Helps to open necessary transactions manually.
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
            final WritableTenantContext tenantContext,
            final TenantIdsSupplier tenantIdsSupplier) {
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
                LOG.info("Create view: {}", view.getEntry().viewClass().getName());
                view.setCronTask(new CronTask(() -> updateView(view), view.getEntry().cron()));
                taskRegistrar.addCronTask(view.getCronTask());
            }
        }
    }

    private void shutdownViews() {
        LOG.info("Shutdown {} view jobs...", viewJobs == null ? 0 : viewJobs.size());
        final Set<ScheduledTask> scheduledTasks = postProcessor.getScheduledTasks();
        for (final ViewJob viewJob : viewJobs) {
            LOG.info("Shutdown job for view: {}", viewJob.getEntry().viewClass().getName());
            scheduledTasks.stream()
                    .filter(scheduled -> scheduled.getTask() == viewJob.getCronTask())
                    .findFirst()
                    .ifPresent(ScheduledTask::cancel);
        }
    }


    private void updateView(final ViewJob viewJob) {
        tryLocked(viewJob.getLock(), () -> new Thread(() -> {
            LOG.debug("updateView({})", viewJob.getEntry().viewClass().getName());
            readStreamEvents(viewJob);
        }
        ).start());
    }

    private void readStreamEvents(@NotNull final ViewJob viewJob) {
        if (tenantIdsSupplier == null) {
            readStreamEvents(null, viewJob);
        } else {
            tenantIdsSupplier.getTenantIds().forEach(tenantId -> {
                tenantContext.setTenantId(tenantId);
                try {
                    readStreamEvents(tenantId, viewJob);
                } finally {
                    tenantContext.clear();
                }
            });
        }
    }

    private void readStreamEvents(@Nullable final TenantId tenantId, @NotNull final ViewJob viewJob) {
        try {

            // Create an event store projection if it does not exist.
            createProjection(tenantId, viewJob);

            // Read and dispatch events
            final StreamId projectionStreamId = projectionStreamId(tenantId, viewJob);
            final Long nextEventNumber = projectionService.readProjectionPosition(projectionStreamId);
            eventstore.readAllEventsForward(projectionStreamId, nextEventNumber, viewJob.getEntry().chunkSize(),
                    currentSlice -> handleChunk(tenantId, projectionStreamId, viewJob, currentSlice));

        } catch (final RuntimeException ex) {
            if (tenantId == null) {
                LOG.error("Error processing events for viewJob '" + viewJob.entry.beanName() + "'", ex);
            } else {
                LOG.error("Error processing events for tenant '" + tenantId + "' viewJob '" + viewJob.entry.beanName() + "'", ex);
            }
        }

    }

    private void createProjection(@Nullable final TenantId tenantId,
                                  @NotNull final ViewJob viewJob) {
        final StreamId streamId;
        if (tenantId == null) {
            streamId = viewJob.getProjectionStreamId();
        } else {
            streamId = new ProjectionStreamId(tenantId.name() + "-" + viewJob.getProjectionStreamId());
        }
        if (!admin.projectionExists(streamId)) {
            final List<TypeName> typeNames = asTypeNames(viewJob.getEntry().eventTypes());
            LOG.info("Create projection '{}'{} with events: {}",
                    viewJob.getProjectionStreamId(), (tenantId == null ? "" : " for tenant '" + tenantId + "'"), typeNames);
            try {
                final SimpleTenantId tid = tenantId == null ? null : new SimpleTenantId(tenantId.name());
                admin.createProjection(tid, viewJob.getProjectionStreamId(), true, typeNames);
            } catch (StreamAlreadyExistsException ex) {
                LOG.info("Race condition: After checking if project exists, the create failed with 'already exists'");
            }
        }
    }

    private List<TypeName> asTypeNames(Set<EventType> eventTypes) {
        return eventTypes.stream().map(eventType -> new TypeName((eventType.asString()))).toList();
    }

    private void handleChunk(final TenantId tenantId, final StreamId projectionStreamId, final ViewJob viewJob, final StreamEventsSlice currentSlice) {
        requiresNewTransaction.execute(new TransactionCallbackWithoutResult() {
            public void doInTransactionWithoutResult(TransactionStatus status) {
                LOG.debug("Handle chunk: {}", currentSlice);
                viewJob.handleEvents(beanFactory, tenantId, asEvents(currentSlice.getEvents()));
                projectionService.updateProjectionPosition(projectionStreamId, currentSlice.getNextEventNumber());
            }
        });
    }

    private static StreamId projectionStreamId(TenantId tenantId, ViewJob viewJob) {
        if (tenantId == null) {
            return viewJob.getProjectionStreamId();
        }
        return new ProjectionStreamId("v_" + tenantId.name() + "-"  + viewJob.getProjectionStreamId());
    }

    private static List<Event> asEvents(List<CommonEvent> events) {
        return events.stream().map(event -> (Event) event.getData()).toList();
    }

    /**
     * Extends the view with some necessary values used only by this class.
     */
    private static class ViewJob {

        private final ViewRegistry.Entry entry;

        private final ProjectionStreamId projectionStreamId;

        private final Semaphore lock;

        private CronTask cronTask;

        public ViewJob(final ViewRegistry.Entry entry) {
            this.entry = Objects.requireNonNull(entry, "entry==null");
            final String streamId = entry.streamName() + "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes());
            projectionStreamId = new ProjectionStreamId(streamId);
            this.lock = new Semaphore(1);
        }

        /**
         * Returns the task used.
         *
         * @return Task.
         */
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
                                 @Nullable final TenantId tenantId,
                                 final List<Event> events) {
            final View view = beanFactory.getBean(entry.beanName(), entry.viewClass());
            try {
                view.handleEvents(tenantId, events);
            } finally {
                beanFactory.destroyBean(entry.beanName(), view);
            }
        }

        public ProjectionStreamId getProjectionStreamId() {
            return projectionStreamId;
        }

        public Semaphore getLock() {
            return lock;
        }

    }

}
