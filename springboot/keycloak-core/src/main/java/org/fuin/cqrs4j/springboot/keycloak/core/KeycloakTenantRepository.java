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
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Tenant repository backed by Keycloak realms. Tenants are discovered lazily by their issuer URI
 * and cached. A {@link TenantAddedEvent} is published whenever a previously unknown tenant is added.
 */
public class KeycloakTenantRepository implements JwtTenantRepository {

    private final String baseUri;

    private final Map<String, JwtTenant> tenantMap;

    private final ApplicationEventPublisher publisher;

    /**
     * Constructor with the master realm issuer URI.
     *
     * @param masterIssuerUri Issuer URI like "http://localhost:8082/realms/master"
     * @param publisher       Helper to inform others about new tenants.
     */
    public KeycloakTenantRepository(String masterIssuerUri,
                                    ApplicationEventPublisher publisher) {
        super();
        Objects.requireNonNull(masterIssuerUri, "masterIssuerUri==null");
        this.publisher = Objects.requireNonNull(publisher, "applicationEventPublisher==null");
        final int p = masterIssuerUri.lastIndexOf('/');
        if (p < 0) {
            throw new IllegalArgumentException("Cannot find realm in: '" + masterIssuerUri + "'");
        }
        this.baseUri = masterIssuerUri.substring(0, p + 1);
        this.tenantMap = new ConcurrentHashMap<>();
    }

    @Override
    public Stream<TenantId> getTenantIds() {
        if (tenantMap.isEmpty()) {
            return Stream.empty();
        }
        return tenantMap.values().stream().map(JwtTenant::getTenantId);
    }

    /**
     * Finds the tenant for the given issuer URI. If the tenant is not yet known it is created,
     * cached and announced via a {@link TenantAddedEvent}.
     *
     * @param issuerUri Issuer URI of the tenant. Must start with the master realm base URI.
     * @return Matching tenant.
     */
    @Override
    public Optional<JwtTenant> findByIssuer(String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuer==null");
        final Optional<JwtTenant> result = tenantMap.entrySet().stream()
                .filter(entry -> entry.getKey().equals(issuerUri))
                .map(Map.Entry::getValue)
                .findFirst();
        if (result.isPresent()) {
            return result;
        }
        if (!issuerUri.startsWith(baseUri)) {
            throw new IllegalArgumentException("Issuer URI '" + issuerUri + "' does not start with '" + baseUri + "'");
        }
        final JwtTenant tenant = new JwtTenant(issuerUri);
        tenantMap.put(issuerUri, tenant);
        publisher.publishEvent(new TenantAddedEvent(tenant));
        return Optional.of(tenant);
    }

}
