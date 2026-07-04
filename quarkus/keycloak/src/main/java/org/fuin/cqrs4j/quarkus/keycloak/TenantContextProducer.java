package org.fuin.cqrs4j.quarkus.keycloak;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.fuin.ddd4j.core.ThreadLocalTenantContext;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Produces the {@link WritableTenantContext} used to propagate the current tenant to the query / projection
 * side. A thread-local backed implementation is used, mirroring the Spring setup where the tenant is bound to
 * the request-processing thread.
 */
@ThreadSafe
public class TenantContextProducer {

    /**
     * Produces the application-wide, thread-local backed writable tenant context.
     *
     * @return Writable tenant context.
     */
    @Produces
    @ApplicationScoped
    public WritableTenantContext writableTenantContext() {
        return new ThreadLocalTenantContext();
    }

}
