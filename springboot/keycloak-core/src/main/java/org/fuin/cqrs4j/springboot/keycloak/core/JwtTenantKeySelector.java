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

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector;
import org.fuin.objects4j.common.ThreadSafe;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts the tenant from the JWT URI and compares it with tenants from a repository.
 * See <a href="https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/multitenancy.html">Multitenancy in Spring Docs</a>.
 */
@ThreadSafe
public class JwtTenantKeySelector implements JWTClaimsSetAwareJWSKeySelector<SecurityContext> {

    private final JwtTenantRepository tenantRepository;

    private final Map<String, JWSKeySelector<SecurityContext>> selectors;

    /**
     * Constructor with tenant repository.
     *
     * @param tenantRepository Tenant repository.
     */
    public JwtTenantKeySelector(JwtTenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository==null");
        this.selectors = new ConcurrentHashMap<>();
    }

    @Override
    public List<? extends Key> selectKeys(JWSHeader jwsHeader,
                                          JWTClaimsSet jwtClaimsSet,
                                          SecurityContext securityContext) throws KeySourceException {
        Objects.requireNonNull(jwsHeader);
        Objects.requireNonNull(jwtClaimsSet);

        return selectors.computeIfAbsent(issuer(jwtClaimsSet), this::fromIssuer)
                .selectJWSKeys(jwsHeader, securityContext);

    }

    private String issuer(JWTClaimsSet claimSet) {
        return (String) claimSet.getClaim("iss");
    }

    private JWSKeySelector<SecurityContext> fromIssuer(String issuer) {
        final JwtTenant tenant = tenantRepository.findByIssuer(issuer)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: '" + issuer + "'"));
        return tenant.getJWSKeySelector();
    }

}