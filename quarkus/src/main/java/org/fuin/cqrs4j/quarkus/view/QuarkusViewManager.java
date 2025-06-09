package org.fuin.cqrs4j.quarkus.view;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.quarkus.base.QuarkusUtils;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.esc.api.SimpleTenantId;
import org.fuin.esc.api.StreamAlreadyExistsException;
import org.fuin.esc.api.StreamEventsSlice;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.TenantStreamId;
import org.fuin.esc.api.TypeName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static org.fuin.utils4j.Utils4J.tryLocked;

/**
 * Creates scheduler update jobs for all classes implementing the {@link View} interface.
 * Avoids boilerplate code: Instead of having a separated "Projector", "EventDispatcher"
 * and a "ChunkHandler" class for each view, there is only one simplified "View" class now.
 */
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
    BeanManager beanManager;

    @Inject
    Instance<TenantIdsSupplier> tenantIdsSupplierInstance;

    private List<ViewExt> views;

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
    }

    @Shutdown
    void shutdownViews() {
        LOG.info("Shutdown views...");
        for (final ViewExt view : views) {
            LOG.info("Shutdown: {}", view.getEntry().viewClass().getSimpleName());
            scheduler.unscheduleJob(view.getEntry().beanName());
        }
    }

    private void updateView(final ViewExt view) {
        tryLocked(view.getLock(), () -> new Thread(() -> {
            LOG.debug("updateView({})", view.getEntry().viewClass().getSimpleName());
            readStreamEvents(view);
        }
        ).start());
    }

    private void readStreamEvents(final ViewExt viewExt) {
        if (tenantIdsSupplierInstance.isUnsatisfied()) {
            readStreamEvents(null, viewExt);
        } else {
            tenantIdsSupplierInstance.get().getTenantIds().forEach(tenantId -> readStreamEvents(tenantId, viewExt));
        }
    }

    private void readStreamEvents(@Nullable final TenantId tenantId, final ViewExt viewExt) {

        // Create an event store projection if it does not exist.
        final StreamId projectionStreamId = projectionStreamId(tenantId, viewExt);
        if (!admin.projectionExists(projectionStreamId(tenantId, viewExt))) {
            createProjection(tenantId, viewExt);
        }

        // Read and dispatch events
        final Long nextEventNumber = projectionService.readProjectionPosition(projectionStreamId);
        eventstore.readAllEventsForward(projectionStreamId, nextEventNumber, viewExt.getEntry().chunkSize(),
                currentSlice -> handleChunk(tenantId, projectionStreamId, viewExt, currentSlice));

    }

    private void createProjection(@Nullable final TenantId tenantId,
                                  @NotNull final ViewExt viewExt) {
        final List<TypeName> typeNames = asTypeNames(viewExt.getEntry().eventTypes());
        LOG.info("Create projection '{}'{} with events: {}",
                viewExt.getProjectionStreamId(), (tenantId == null ? "" : " for tenant '" + tenantId + "'"), typeNames);
        try {
            final SimpleTenantId tid = tenantId == null ? null : new SimpleTenantId(tenantId.name());
            admin.createProjection(tid, viewExt.getProjectionStreamId(), true, typeNames);
        } catch (StreamAlreadyExistsException ex) {
            LOG.info("Race condition: After checking if project exists, the create failed with 'already exists'");
        }
    }

    private List<TypeName> asTypeNames(Set<EventType> eventTypes) {
        return eventTypes.stream().map(eventType -> new TypeName((eventType.asString()))).toList();
    }

    private void handleChunk(@Nullable final TenantId tenantId,
                             final StreamId projectionStreamId,
                             final ViewExt viewExt,
                             final StreamEventsSlice currentSlice) {
        QuarkusTransaction.requiringNew()
                .timeout(10)
                .call(() -> {
                    LOG.debug("Handle chunk: {}", currentSlice);
                    viewExt.handleEvents(beanManager, tenantId, asEvents(currentSlice.getEvents()));
                    projectionService.updateProjectionPosition(projectionStreamId, currentSlice.getNextEventNumber());
                    return 0;
                });
    }

    private static StreamId projectionStreamId(TenantId tenantId, ViewExt viewExt) {
        if (tenantId == null) {
            return viewExt.getProjectionStreamId();
        }
        return new TenantStreamId(new SimpleTenantId(tenantId.name()), viewExt.getProjectionStreamId());
    }

    private List<org.fuin.ddd4j.core.Event> asEvents(List<CommonEvent> events) {
        return events.stream().map(event -> (Event) event.getData()).toList();
    }

    /**
     * Extends the view with some necessary values used only by this class.
     */
    private static class ViewExt {

        private final ViewRegistry.Entry entry;

        private final ProjectionStreamId projectionStreamId;

        private final Semaphore lock;

        public ViewExt(final ViewRegistry.Entry entry) {
            this.entry = entry;
            final String streamId = entry.streamName() + "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes());
            projectionStreamId = new ProjectionStreamId(streamId);
            this.lock = new Semaphore(1);

        }

        public ViewRegistry.Entry getEntry() {
            return entry;
        }

        public void handleEvents(BeanManager beanManager, TenantId tenantId, List<Event> events) {
            final View view = getView(beanManager, entry);
            view.handleEvents(tenantId, events);
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
