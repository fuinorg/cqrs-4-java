/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.springboot.keycloak.core;

import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically asks a {@link KeycloakTenantRepository} to recheck the tenants it has discovered, so a realm
 * that was deleted or disabled in Keycloak stops being a valid tenant without an application restart.
 * <p>
 * Deliberately driven by its <b>own</b> executor rather than by {@code @Scheduled}: a {@code @Scheduled}
 * method in a library silently does nothing in an application that never enabled scheduling, and a
 * revocation mechanism that quietly does not run is worse than none, because it looks configured. The same
 * approach is used by the view manager for its resubscribe loop.
 * <p>
 * If the application replaced the repository with another implementation - a fixed single-realm one, or a
 * registry fed from a control plane - there is nothing to revalidate here and this does nothing.
 */
@ThreadSafe
public class TenantRevalidator implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TenantRevalidator.class);

    @Nullable
    private final ScheduledExecutorService scheduler;

    /**
     * Constructor starting the sweep if the repository supports it.
     *
     * @param tenantRepository Repository whose tenants should be rechecked.
     * @param intervalMillis   Delay between two sweeps.
     */
    public TenantRevalidator(final JwtTenantRepository tenantRepository, final long intervalMillis) {
        Objects.requireNonNull(tenantRepository, "tenantRepository==null");
        if (intervalMillis < 1) {
            throw new IllegalArgumentException("intervalMillis must be positive, but was: " + intervalMillis);
        }
        if (tenantRepository instanceof KeycloakTenantRepository repository) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "tenant-revalidate");
                thread.setDaemon(true);
                return thread;
            });
            this.scheduler.scheduleWithFixedDelay(() -> sweep(repository),
                    intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
            LOG.info("Tenant revalidation enabled, every {} ms", intervalMillis);
        } else {
            this.scheduler = null;
            LOG.debug("No tenant revalidation for repository of type {}", tenantRepository.getClass().getName());
        }
    }

    @Override
    public void close() {
        final ScheduledExecutorService current = scheduler;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private static void sweep(final KeycloakTenantRepository repository) {
        try {
            repository.revalidate();
        } catch (final RuntimeException ex) {
            // Never let a failure kill the scheduled task - scheduleWithFixedDelay stops on a throw.
            LOG.error("Tenant revalidation sweep failed", ex);
        }
    }

}
