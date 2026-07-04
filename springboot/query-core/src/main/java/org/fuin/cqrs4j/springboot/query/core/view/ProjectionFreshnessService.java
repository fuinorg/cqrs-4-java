package org.fuin.cqrs4j.springboot.query.core.view;

import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionFreshness;
import org.fuin.cqrs4j.esc.ProjectionFreshness.Freshness;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.esc.ProjectionStreamIds;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.stereotype.Service;

/**
 * Answers "how fresh is view X" for read clients: the current checkpoint {@code position} and the full
 * {@link Freshness} (position, lag, caught-up). Reads run in the ambient tenant context (the routing datasource
 * scopes them), so a client asking about its own read gets its own tenant's answer. {@link #position(String)} is
 * a cheap checkpoint lookup suitable for a per-request header; {@link #freshness(String)} additionally reads the
 * event store, so it is better used on a dedicated freshness endpoint.
 */
@ThreadSafe
@Service
public class ProjectionFreshnessService {

    private final ViewRegistry viewRegistry;

    private final EventStore eventstore;

    private final ProjectionService projectionService;

    /**
     * Constructor with mandatory data.
     *
     * @param viewRegistry      Registered views.
     * @param eventstore        Event store the projection streams live in.
     * @param projectionService Service holding the projection checkpoints.
     */
    public ProjectionFreshnessService(final ViewRegistry viewRegistry,
                                      final EventStore eventstore,
                                      final ProjectionService projectionService) {
        super();
        Contract.requireArgNotNull("viewRegistry", viewRegistry);
        Contract.requireArgNotNull("eventstore", eventstore);
        Contract.requireArgNotNull("projectionService", projectionService);
        this.viewRegistry = viewRegistry;
        this.eventstore = eventstore;
        this.projectionService = projectionService;
    }

    /**
     * Returns the current checkpoint position of a view ("the read model is current as of position N"). Cheap
     * (a single checkpoint read).
     *
     * @param viewName Name of the view.
     * @return Current projection position (number of the next event to read).
     */
    public long position(final String viewName) {
        return projectionService.readProjectionPosition(streamId(viewName));
    }

    /**
     * Returns the full freshness of a view (position, lag, caught-up). Reads the event store forward to compute
     * the lag.
     *
     * @param viewName Name of the view.
     * @return Freshness of the view.
     */
    public Freshness freshness(final String viewName) {
        final ViewRegistry.Entry entry = entry(viewName);
        return ProjectionFreshness.of(eventstore, projectionService, ProjectionStreamIds.of(entry), entry.chunkSize());
    }

    private ProjectionStreamId streamId(final String viewName) {
        return ProjectionStreamIds.of(entry(viewName));
    }

    private ViewRegistry.Entry entry(final String viewName) {
        Contract.requireArgNotNull("viewName", viewName);
        return viewRegistry.getViews().stream()
                .filter(e -> e.name().equals(viewName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown view: " + viewName));
    }

}
