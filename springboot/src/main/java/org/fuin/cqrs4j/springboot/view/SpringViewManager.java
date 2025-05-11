package org.fuin.cqrs4j.springboot.view;

import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.StreamAlreadyExistsException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.TypeName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static org.fuin.utils4j.Utils4J.tryLocked;

/**
 * Creates scheduler update tasks for all classes implementing the {@link View} interface.
 * Avoids boilerplate code: Instead of having a separated "Projector", "EventDispatcher"
 * and a "ChunkHandler" class for each view, there is only one simplified "View" class now.
 */
@Component
@Order(0)
public class SpringViewManager implements ApplicationListener<ContextClosedEvent>, SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringViewManager.class);

    private final ScheduledAnnotationBeanPostProcessor postProcessor;

    private final ViewRegistry viewRegistry;

    private final EventStore eventstore;

    private final ProjectionAdminEventStore admin;

    private final ProjectionService projectionService;

    private final TransactionTemplate requiresNewTransaction;

    private final ConfigurableBeanFactory beanFactory;

    private List<ViewJob> views;

    /**
     * Constructor with mandatory data.
     *
     * @param postProcessor      Helps to cancel the scheduled jobs ob shutdown.
     * @param viewRegistry       List with user defined view classes.
     * @param eventstore         Eventstore instance to use.
     * @param admin              Admin interface to eventstore.
     * @param projectionService  Service to manage projections.
     * @param transactionManager Helps to open necessary transactions manually.
     * @param beanFactory        Bean factory.
     */
    public SpringViewManager(
            final ScheduledAnnotationBeanPostProcessor postProcessor,
            final ViewRegistry viewRegistry,
            final EventStore eventstore,
            final ProjectionAdminEventStore admin,
            final ProjectionService projectionService,
            final PlatformTransactionManager transactionManager,
            final ConfigurableBeanFactory beanFactory) {
        this.postProcessor = Objects.requireNonNull(postProcessor, "postProcessor==null");
        this.viewRegistry = Objects.requireNonNull(viewRegistry, "viewClassRegistry==null");
        this.eventstore = Objects.requireNonNull(eventstore, "eventstore==null");
        this.admin = Objects.requireNonNull(admin, "admin==null");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService==null");
        Objects.requireNonNull(transactionManager, "transactionManager==null");
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTransaction.setTimeout(10);
        this.beanFactory = beanFactory;
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
        LOG.info("Create {} views...", viewRegistry.size());
        if (viewRegistry.isEmpty()) {
            views = Collections.emptyList();
        } else {
            views = viewRegistry.getViews().stream()
                    .map(ViewJob::new)
                    .toList();
            for (final ViewJob view : views) {
                LOG.info("Create view: {}", view.getEntry().viewClass().getName());
                view.setCronTask(new CronTask(() -> updateView(view), view.getEntry().cron()));
                taskRegistrar.addCronTask(view.getCronTask());
            }
        }
    }

    private void shutdownViews() {
        LOG.info("Shutdown {} views...", views == null ? 0 : views.size());
        final Set<ScheduledTask> scheduledTasks = postProcessor.getScheduledTasks();
        for (final ViewJob view : views) {
            LOG.info("Shutdown view: {}", view.getEntry().viewClass().getName());
            scheduledTasks.stream()
                    .filter(scheduled -> scheduled.getTask() == view.getCronTask())
                    .findFirst()
                    .ifPresent(ScheduledTask::cancel);
        }
    }


    private void updateView(final ViewJob view) {
        tryLocked(view.getLock(), () -> new Thread(() -> {
            try {
                LOG.debug("updateView({})", view.getEntry().viewClass().getName());
                readStreamEvents(view);
            } catch (final RuntimeException ex) {
                LOG.error("Error reading events from stream", ex);
            }
        }
        ).start());
    }

    private void readStreamEvents(final ViewJob view) {

        // Create an event store projection if it does not exist.
        if (!admin.projectionExists(view.getProjectionStreamId())) {
            final List<TypeName> typeNames = asTypeNames(view.getEntry().eventTypes());
            LOG.info("Create projection '{}' with events: {}", view.getProjectionStreamId(), typeNames);
            try {
                admin.createProjection(view.getProjectionStreamId(), true, typeNames);
            } catch (StreamAlreadyExistsException ex) {
                LOG.info("Race condition: After projectionExists({}) create failed with 'already exists'", view.getProjectionStreamId());
            }
        }

        // Read and dispatch events
        final Long nextEventNumber = projectionService.readProjectionPosition(view.getProjectionStreamId());
        eventstore.readAllEventsForward(view.getProjectionStreamId(), nextEventNumber, view.getEntry().chunkSize(),
                currentSlice -> handleChunk(view, currentSlice));

    }

    private List<TypeName> asTypeNames(Set<EventType> eventTypes) {
        return eventTypes.stream().map(eventType -> new TypeName((eventType.asString()))).toList();
    }

    private void handleChunk(final ViewJob view, final StreamEventsSlice currentSlice) {
        requiresNewTransaction.execute(new TransactionCallbackWithoutResult() {
            public void doInTransactionWithoutResult(TransactionStatus status) {
                LOG.debug("Handle chunk: {}", currentSlice);
                view.handleEvents(beanFactory, asEvents(currentSlice.getEvents()));
                projectionService.updateProjectionPosition(view.getProjectionStreamId(), currentSlice.getNextEventNumber());
            }
        });
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
            this.entry = entry;
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

        public void handleEvents(ConfigurableBeanFactory beanFactory, List<Event> events) {
            final View view = beanFactory.getBean(entry.beanName(), entry.viewClass());
            try {
                view.handleEvents(events);
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
