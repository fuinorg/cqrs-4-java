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

import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tenant repository that accepts exactly <b>one</b> configured issuer - the repository for an application
 * that is not multi-tenant.
 * <p>
 * {@link KeycloakTenantRepository} discovers every realm below the master realm's base URI on demand,
 * <b>regardless of whether multi-tenancy is enabled</b>. A single-tenant application using it therefore
 * accepts a token from any realm of that Keycloak instance, including realms created for something else
 * entirely. Declaring this repository as a bean replaces it (the auto-configuration's own repository is
 * conditional on none being present) and pins the trust boundary to one realm.
 * <p>
 * The tenant is resolved lazily on first use, so no Keycloak connection is needed while the context
 * starts, and it is resolved only once.
 */
@ThreadSafe
public class SingleRealmTenantRepository implements JwtTenantRepository {

    private final String issuerUri;

    private final Object lock = new Object();

    @Nullable
    private volatile JwtTenant tenant;

    /**
     * Constructor with the one accepted issuer.
     *
     * @param issuerUri Issuer URI of the only accepted realm, like "http://localhost:8082/realms/master".
     */
    public SingleRealmTenantRepository(final String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuerUri==null");
        if (issuerUri.isBlank()) {
            throw new IllegalArgumentException("issuerUri is empty");
        }
        this.issuerUri = issuerUri;
    }

    @Override
    public Optional<JwtTenant> findByIssuer(final String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuerUri==null");
        if (!this.issuerUri.equals(issuerUri)) {
            // Not "unknown tenant, discover it" - this application serves exactly one realm.
            return Optional.empty();
        }
        return Optional.of(resolve());
    }

    @Override
    public Stream<TenantId> getTenantIds() {
        return Stream.of(resolve().getTenantId());
    }

    /**
     * Returns the issuer this repository accepts.
     *
     * @return Issuer URI, never empty.
     */
    public String getIssuerUri() {
        return issuerUri;
    }

    private JwtTenant resolve() {
        JwtTenant result = tenant;
        if (result == null) {
            synchronized (lock) {
                result = tenant;
                if (result == null) {
                    result = new JwtTenant(issuerUri);
                    tenant = result;
                }
            }
        }
        return result;
    }

}
