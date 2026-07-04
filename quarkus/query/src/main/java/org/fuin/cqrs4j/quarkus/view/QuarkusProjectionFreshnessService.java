package org.fuin.cqrs4j.quarkus.view;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionFreshness;
import org.fuin.cqrs4j.esc.ProjectionFreshness.Freshness;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.esc.ProjectionStreamIds;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Answers "how fresh is view X" for read clients: the current checkpoint {@code position} and the full
 * {@link Freshness} (position, lag, caught-up). Reads run in the ambient tenant context (the routing datasource
 * scopes them), so a client asking about its own read gets its own tenant's answer. {@link #position(String)} is
 * a cheap checkpoint lookup suitable for a per-request header; {@link #freshness(String)} additionally reads the
 * event store, so it is better used on a dedicated freshness endpoint.
 */
@ThreadSafe
@ApplicationScoped
public class QuarkusProjectionFreshnessService {

    @Inject
    ViewRegistry viewRegistry;

    @Inject
    EventStore eventstore;

    @Inject
    ProjectionService projectionService;

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
