package org.fuin.cqrs4j.quarkus.base;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.core.KeyValueEL;
import org.fuin.objects4j.core.Validators;

/**
 * Removes the state objects4j binds to the current thread once the response is sent: the {@link KeyValueEL}
 * {@code ELProcessor} and the {@link Validators} {@code Validator}. Because request threads are pooled, they would
 * otherwise be retained after the last request and leak memory (and in case of the {@code ELProcessor} its beans would
 * bleed into a subsequent request handled by the same thread). Mirrors the Spring
 * {@code ThreadLocalCleanupInterceptor}.
 */
@ThreadSafe
@Provider
@ApplicationScoped
public class ThreadLocalCleanupResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) {
        KeyValueEL.clear();
        Validators.clear();
    }

}
