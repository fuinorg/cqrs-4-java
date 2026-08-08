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
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link JwtTenantIssuerValidator}.
 */
class JwtTenantIssuerValidatorTest {

    private static final String ISSUER = "http://localhost:8080/realms/acme";

    @Test
    void testKnownIssuerPasses() {

        // PREPARE
        final TestRepository repository = new TestRepository();
        repository.add(ISSUER);
        final JwtTenantIssuerValidator testee = new JwtTenantIssuerValidator(repository);

        // TEST
        final OAuth2TokenValidatorResult result = testee.validate(jwt(ISSUER));

        // VERIFY
        assertThat(result.hasErrors()).isFalse();

    }

    @Test
    void testUnknownIssuerRejected() {

        // PREPARE
        final JwtTenantIssuerValidator testee = new JwtTenantIssuerValidator(new TestRepository());

        // TEST & VERIFY
        assertThatThrownBy(() -> testee.validate(jwt(ISSUER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ISSUER);

    }

    /**
     * The test this class exists for: a resolved issuer used to be cached forever, so dropping the tenant
     * from the repository revoked nothing on a running instance. Without the eviction the second validate
     * below still passes.
     */
    @Test
    void testRemovedTenantIsRejectedAfterwards() {

        // PREPARE
        final TestRepository repository = new TestRepository();
        final JwtTenant tenant = repository.add(ISSUER);
        final JwtTenantIssuerValidator testee = new JwtTenantIssuerValidator(repository);
        assertThat(testee.validate(jwt(ISSUER)).hasErrors()).isFalse();

        // TEST
        repository.remove(ISSUER);
        testee.onTenantRemoved(new TenantRemovedEvent(tenant));

        // VERIFY
        assertThatThrownBy(() -> testee.validate(jwt(ISSUER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ISSUER);

    }

    @Test
    void testRemovingAnUnknownTenantDoesNothing() {

        // PREPARE
        final TestRepository repository = new TestRepository();
        repository.add(ISSUER);
        final JwtTenantIssuerValidator testee = new JwtTenantIssuerValidator(repository);
        assertThat(testee.validate(jwt(ISSUER)).hasErrors()).isFalse();

        // TEST
        testee.evict(new TenantId("other"));

        // VERIFY - the cached tenant is untouched
        assertThat(testee.validate(jwt(ISSUER)).hasErrors()).isFalse();

    }

    private static Jwt jwt(final String issuer) {
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("iss", issuer, "sub", "peter"));
    }

    /**
     * Repository backed by a map, so a tenant can be added and removed without a Keycloak.
     */
    private static final class TestRepository implements JwtTenantRepository {

        private final Map<String, JwtTenant> tenants = new HashMap<>();

        JwtTenant add(final String issuerUri) {
            // Attributes constructor - no OIDC discovery call.
            final JwtTenant tenant = new JwtTenant(issuerUri,
                    Map.of("issuer", issuerUri, "jwks_uri", issuerUri + "/protocol/openid-connect/certs"));
            tenants.put(issuerUri, tenant);
            return tenant;
        }

        void remove(final String issuerUri) {
            tenants.remove(issuerUri);
        }

        @Override
        public Optional<JwtTenant> findByIssuer(final String issuerUri) {
            return Optional.ofNullable(tenants.get(issuerUri));
        }

        @Override
        public Stream<TenantId> getTenantIds() {
            return tenants.values().stream().map(JwtTenant::getTenantId);
        }

    }

}
