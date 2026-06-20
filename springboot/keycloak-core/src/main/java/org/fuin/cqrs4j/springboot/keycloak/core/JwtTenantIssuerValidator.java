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

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates that a given tenant is known and the "iss" claim in a Jwt matches a configured value.
 * See <a href="https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/multitenancy.html">Multitenancy in Spring Docs</a>.
 */
public class JwtTenantIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private final JwtTenantRepository tenantRepository;

    private final Map<String, JwtIssuerValidator> validatorCache;

    /**
     * Constructor with tenant repository.
     *
     * @param tenantRepository Has the known tenants.
     */
    public JwtTenantIssuerValidator(JwtTenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository==null");
        this.validatorCache = new ConcurrentHashMap<>();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return this.validatorCache.computeIfAbsent(issuer(token), this::fromIssuer).validate(token);
    }

    private String issuer(Jwt jwt) {
        return jwt.getIssuer().toString();
    }

    private JwtIssuerValidator fromIssuer(String issuer) {
        return new JwtIssuerValidator(tenantRepository
                .findByIssuer(issuer)
                .orElseThrow(() -> new IllegalArgumentException("Unknown issuer: '" + issuer + "'"))
                .getIssuer());
    }

}