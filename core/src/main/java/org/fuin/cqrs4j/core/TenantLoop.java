package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Runs background work once per tenant, with that tenant on the thread.
 *
 * <p>Anything driven by a scheduler rather than by a request has no token to take a tenant from, so it has
 * to put one there itself: projections, outbox drains, timeout sweeps. Every one of them is a loop with the
 * same three properties, and getting any of them wrong is quiet rather than loud, which is why they are
 * written once here.
 *
 * <ul>
 * <li><b>The tenant is cleared afterwards</b>, on the way out of every iteration. These run on pooled
 * scheduler threads, so a tenant left behind is inherited by whatever runs next.</li>
 * <li><b>One tenant's failure does not stop the others.</b> An unreachable stream or a locked table for one
 * tenant must not starve every other tenant of its sweep - the alternative is an outage for everyone
 * whenever any one tenant has a bad minute.</li>
 * <li><b>No tenants configured means run once, plainly.</b> Single-tenant deployments keep exactly the
 * behaviour they had before there was a loop.</li>
 * </ul>
 */
@ThreadSafe
public final class TenantLoop {

    private static final Logger LOG = LoggerFactory.getLogger(TenantLoop.class);

    private TenantLoop() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of a utility class");
    }

    /**
     * Runs the given work once per known tenant, or exactly once when multitenancy is not configured.
     *
     * @param context   Context to put each tenant on the thread with; {@literal null} when single-tenant.
     * @param tenantIds Supplies the tenants known at this moment; {@literal null} when single-tenant.
     * @param work      What to do for each tenant. Runs with the tenant already on the thread.
     */
    public static void run(@Nullable final WritableTenantContext context,
                           @Nullable final TenantIdsSupplier tenantIds, final Runnable work) {
        Objects.requireNonNull(work, "work==null");
        if (context == null || tenantIds == null) {
            work.run();
            return;
        }
        tenantIds.getTenantIds().forEach(tenantId -> runOne(context, tenantId, work));
    }

    private static void runOne(final WritableTenantContext context, final TenantId tenantId,
                               final Runnable work) {
        context.setTenantId(tenantId);
        try {
            work.run();
        } catch (final RuntimeException ex) { // NOSONAR - one tenant's failure is not every tenant's
            LOG.error("Failed for tenant '{}' - continuing with the remaining tenants",
                    tenantId.asString(), ex);
        } finally {
            context.clear();
        }
    }

}
