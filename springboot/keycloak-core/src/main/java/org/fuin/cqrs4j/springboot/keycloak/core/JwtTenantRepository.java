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

import org.fuin.cqrs4j.core.TenantRepository;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Optional;

/**
 * A {@link TenantRepository} that can additionally resolve tenants by their JWT issuer URI.
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface JwtTenantRepository extends TenantRepository {

    /**
     * Finds the tenant for the given issuer URI. If the tenant is not yet known it is created,
     * cached and announced via a {@code TenantAddedEvent}.
     *
     * @param issuerUri Issuer URI of the tenant. Must start with the master realm base URI.
     * @return Matching tenant.
     */
    Optional<JwtTenant> findByIssuer(String issuerUri);

}
