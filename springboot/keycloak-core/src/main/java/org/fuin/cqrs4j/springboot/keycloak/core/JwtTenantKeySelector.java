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
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts the tenant from the JWT URI and compares it with tenants from a repository.
 * See <a href="https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/multitenancy.html">Multitenancy in Spring Docs</a>.
 * <p>
 * Resolved key selectors are cached, so a tenant that goes away must be <b>evicted</b> - see
 * {@link #onTenantRemoved(TenantRemovedEvent)}. Otherwise the cached selector keeps verifying signatures
 * of a tenant the repository no longer knows.
 */
@ThreadSafe
public class JwtTenantKeySelector implements JWTClaimsSetAwareJWSKeySelector<SecurityContext> {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTenantKeySelector.class);

    private final JwtTenantRepository tenantRepository;

    private final Map<String, JWSKeySelector<SecurityContext>> selectors;

    /** Reverse index, so a removed tenant can be evicted without knowing its issuer URI. */
    private final Map<TenantId, String> issuerByTenantId;

    /**
     * Constructor with tenant repository.
     *
     * @param tenantRepository Tenant repository.
     */
    public JwtTenantKeySelector(JwtTenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository==null");
        this.selectors = new ConcurrentHashMap<>();
        this.issuerByTenantId = new ConcurrentHashMap<>();
    }

    /**
     * Drops the cached key selector of a tenant that is no longer known.
     *
     * @param event Carries the tenant that was removed.
     */
    @EventListener
    public void onTenantRemoved(final TenantRemovedEvent event) {
        evict(event.tenant().getTenantId());
    }

    /**
     * Drops the cached key selector for a tenant. Does nothing if nothing was cached for it.
     *
     * @param tenantId Tenant to forget.
     */
    public void evict(final TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId==null");
        final String issuer = issuerByTenantId.remove(tenantId);
        if (issuer != null) {
            selectors.remove(issuer);
            LOG.info("Evicted key selector of removed tenant: {} ({})", tenantId.name(), issuer);
        }
    }

    @Override
    public List<? extends Key> selectKeys(JWSHeader jwsHeader,
                                          JWTClaimsSet jwtClaimsSet,
                                          SecurityContext securityContext) throws KeySourceException {
        Objects.requireNonNull(jwsHeader);
        Objects.requireNonNull(jwtClaimsSet);

        return selectorFor(issuer(jwtClaimsSet)).selectJWSKeys(jwsHeader, securityContext);

    }

    /**
     * Returns the key selector for an issuer, resolving it on first use.
     * <p>
     * Deliberately <b>not</b> {@code computeIfAbsent}: resolving an issuer performs OIDC discovery and a JWK
     * set fetch, and {@code ConcurrentHashMap} runs the mapping function while holding the lock for that
     * bin. Doing network I/O there makes every concurrent request whose issuer maps to the same bin queue up
     * behind it - exactly when the identity provider is slow and that hurts most - and the JDK documents
     * that the mapping function must not attempt to update the map, which resolving an issuer does
     * indirectly through the tenant repository.
     * <p>
     * The cost is that two requests arriving together for an unknown issuer may both resolve it. That is
     * harmless: the work is idempotent and the first result wins.
     *
     * @param issuer Issuer of the token.
     * @return Key selector for that issuer.
     */
    private JWSKeySelector<SecurityContext> selectorFor(final String issuer) {
        final JWSKeySelector<SecurityContext> known = selectors.get(issuer);
        if (known != null) {
            return known;
        }
        final JwtTenant tenant = tenantRepository.findByIssuer(issuer)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: '" + issuer + "'"));
        final JWSKeySelector<SecurityContext> resolved = tenant.getJWSKeySelector();
        final JWSKeySelector<SecurityContext> raced = selectors.putIfAbsent(issuer, resolved);
        issuerByTenantId.put(tenant.getTenantId(), issuer);
        return raced == null ? resolved : raced;
    }

    private String issuer(JWTClaimsSet claimSet) {
        return (String) claimSet.getClaim("iss");
    }

}