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
import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates that a given tenant is known and the "iss" claim in a Jwt matches a configured value.
 * See <a href="https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/multitenancy.html">Multitenancy in Spring Docs</a>.
 * <p>
 * Resolved issuers are cached, so a tenant that goes away must be <b>evicted</b> - see
 * {@link #onTenantRemoved(TenantRemovedEvent)}. Without that eviction the cached validator keeps accepting
 * tokens of a tenant the repository no longer knows, and removing a tenant revokes nothing until restart.
 */
@ThreadSafe
public class JwtTenantIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTenantIssuerValidator.class);

    private final JwtTenantRepository tenantRepository;

    private final Map<String, JwtIssuerValidator> validatorCache;

    /** Reverse index, so a removed tenant can be evicted without knowing its issuer URI. */
    private final Map<TenantId, String> issuerByTenantId;

    /**
     * Constructor with tenant repository.
     *
     * @param tenantRepository Has the known tenants.
     */
    public JwtTenantIssuerValidator(JwtTenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository==null");
        this.validatorCache = new ConcurrentHashMap<>();
        this.issuerByTenantId = new ConcurrentHashMap<>();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return validatorFor(issuer(token)).validate(token);
    }

    /**
     * Drops the cached validator of a tenant that is no longer known, so its tokens stop being accepted.
     *
     * @param event Carries the tenant that was removed.
     */
    @EventListener
    public void onTenantRemoved(final TenantRemovedEvent event) {
        evict(event.tenant().getTenantId());
    }

    /**
     * Drops the cached validator for a tenant. Does nothing if nothing was cached for it.
     *
     * @param tenantId Tenant to forget.
     */
    public void evict(final TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId==null");
        final String issuer = issuerByTenantId.remove(tenantId);
        if (issuer != null) {
            validatorCache.remove(issuer);
            LOG.info("Evicted issuer validator of removed tenant: {} ({})", tenantId.name(), issuer);
        }
    }

    /**
     * Returns the validator for an issuer, resolving it on first use.
     * <p>
     * Deliberately <b>not</b> {@code computeIfAbsent}: resolving an issuer goes through the tenant
     * repository, which may perform OIDC discovery, and {@code ConcurrentHashMap} runs the mapping function
     * while holding the lock for that bin - see {@link JwtTenantKeySelector} for the same reasoning. Two
     * concurrent requests for an unknown issuer may both resolve it; that is harmless and the first wins.
     *
     * @param issuer Issuer of the token.
     * @return Validator for that issuer.
     */
    private JwtIssuerValidator validatorFor(final String issuer) {
        final JwtIssuerValidator known = validatorCache.get(issuer);
        if (known != null) {
            return known;
        }
        final JwtTenant tenant = tenantRepository
                .findByIssuer(issuer)
                .orElseThrow(() -> new IllegalArgumentException("Unknown issuer: '" + issuer + "'"));
        final JwtIssuerValidator resolved = new JwtIssuerValidator(tenant.getIssuer());
        @Nullable final JwtIssuerValidator raced = validatorCache.putIfAbsent(issuer, resolved);
        issuerByTenantId.put(tenant.getTenantId(), issuer);
        return raced == null ? resolved : raced;
    }

    private String issuer(Jwt jwt) {
        return jwt.getIssuer().toString();
    }

}