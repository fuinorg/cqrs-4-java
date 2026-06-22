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

import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.Tenant;
import org.fuin.objects4j.common.Immutable;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a tenant including its settings.
 */
@Immutable
public class JwtTenant implements Tenant {

    private final TenantId tenantId;

    private final Map<String, Object> attributes;

    /**
     * Constructor with the issuer URI. The tenant identifier is derived from the realm name at the
     * end of the URI and the OpenID Connect configuration is loaded from the issuer.
     *
     * @param issuerUri Issuer URI like "http://localhost:8082/realms/master".
     */
    public JwtTenant(String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuerUri==null");
        final int p = issuerUri.lastIndexOf('/');
        final String realm = issuerUri.substring(p + 1);
        tenantId = new TenantId(realm);
        attributes = JwtUtils.getConfigurationForOidcIssuerLocation(issuerUri);
    }

    /**
     * Returns the unique tenant identifier.
     *
     * @return Tenant identifier derived from the realm name.
     */
    public TenantId getTenantId() {
        return tenantId;
    }

    /**
     * Returns a key selector that resolves the verification keys from the tenant's JWK set URI.
     *
     * @return Key selector used to verify the signature of the tenant's tokens.
     */
    public JWSKeySelector<SecurityContext> getJWSKeySelector() {
        try {
            final String uri = getString("jwks_uri")
                    .orElseThrow(() -> new IllegalArgumentException("Failed to find attribute 'jwks_uri': " + attributes));
            return JWSAlgorithmFamilyJWSKeySelector.fromJWKSetURL(URI.create(uri).toURL());
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Returns a value from the tenant's settings.
     *
     * @param attribute Key of the attribute to read.
     * @return Value.
     */
    public Optional<Object> getAttribute(String attribute) {
        return Optional.ofNullable(attributes.get(attribute));
    }

    /**
     * Returns a value from the tenant's settings.
     *
     * @param attribute Key of the attribute to read.
     * @return Value.
     */
    public Optional<String> getString(String attribute) {
        final Object value = attributes.get(attribute);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String v) {
            return Optional.of(v);
        }
        throw new IllegalArgumentException(attribute + " is not a string: " + value);
    }

    /**
     * Returns the issuer attribute.
     *
     * @return Issuer.
     */
    public String getIssuer() {
        return getString("issuer")
                .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: 'issuer'"));
    }

}
