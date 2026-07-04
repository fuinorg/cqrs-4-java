package org.fuin.cqrs4j.springboot.query.core.view;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionLag;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

/**
 * Exposes the projection lag - the number of events each view still has to process - as a Micrometer gauge
 * tagged with the view name. The lag is derived from the difference between the head of the projection stream
 * and the stored checkpoint (see {@link ProjectionLag}). When multitenancy is enabled the lag is summed over
 * all known tenants. Being a {@link MeterBinder}, it is bound automatically to every {@link MeterRegistry} the
 * application configures; without Micrometer the bean is simply never bound.
 */
@ThreadSafe
public class ProjectionLagMetrics implements MeterBinder {

    /** Gauge name for the projection lag (tagged with {@code view}). */
    public static final String PROJECTION_LAG = "cqrs4j.projection.lag";

    private final ViewRegistry viewRegistry;

    private final EventStore eventstore;

    private final ProjectionService projectionService;

    @Nullable
    private final WritableTenantContext tenantContext;

    @Nullable
    private final TenantIdsSupplier tenantIdsSupplier;

    /**
     * Constructor with mandatory data.
     *
     * @param viewRegistry      Registered views.
     * @param eventstore        Event store the projection streams live in.
     * @param projectionService Service holding the projection checkpoints.
     * @param tenantContext     Tenant context (only used when multitenancy is enabled, may be {@literal null}).
     * @param tenantIdsSupplier Supplies the known tenant ids (only used when multitenancy is enabled, may be
     *                          {@literal null}).
     */
    public ProjectionLagMetrics(final ViewRegistry viewRegistry,
                                final EventStore eventstore,
                                final ProjectionService projectionService,
                                @Nullable final WritableTenantContext tenantContext,
                                @Nullable final TenantIdsSupplier tenantIdsSupplier) {
        super();
        Contract.requireArgNotNull("viewRegistry", viewRegistry);
        Contract.requireArgNotNull("eventstore", eventstore);
        Contract.requireArgNotNull("projectionService", projectionService);
        this.viewRegistry = viewRegistry;
        this.eventstore = eventstore;
        this.projectionService = projectionService;
        this.tenantContext = tenantContext;
        this.tenantIdsSupplier = tenantIdsSupplier;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        for (final ViewRegistry.Entry entry : viewRegistry.getViews()) {
            Gauge.builder(PROJECTION_LAG, this, metrics -> metrics.lagFor(entry))
                    .description("Number of events the projection still has to process (head - checkpoint)")
                    .tag("view", entry.name())
                    .register(registry);
        }
    }

    private double lagFor(final ViewRegistry.Entry entry) {
        final ProjectionStreamId streamId = new ProjectionStreamId(
                entry.streamName() + "-" + CqrsUtils.calculateAdler32Checksum(entry.eventTypes()));
        final TenantIdsSupplier supplier = this.tenantIdsSupplier;
        final WritableTenantContext tc = this.tenantContext;
        if (supplier == null || tc == null) {
            return ProjectionLag.unprocessedEventCount(eventstore, projectionService, streamId, entry.chunkSize());
        }
        return supplier.getTenantIds().mapToLong(tenantId -> {
            tc.setTenantId(tenantId);
            try {
                return ProjectionLag.unprocessedEventCount(eventstore, projectionService, streamId, entry.chunkSize());
            } finally {
                tc.clear();
            }
        }).sum();
    }

}
