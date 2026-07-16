package org.fuin.cqrs4j.quarkus.base;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * Removes the thread-bound {@link KeyValueEL} {@code ELProcessor} once the response is sent. Because request threads
 * are pooled, the per-thread processor would otherwise retain the beans of the last request and leak memory (and
 * bleed into a subsequent request handled by the same thread). Mirrors the Spring {@code KeyValueELCleanupInterceptor}.
 */
@ThreadSafe
@Provider
@ApplicationScoped
public class KeyValueELCleanupResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) {
        KeyValueEL.clear();
    }

}
