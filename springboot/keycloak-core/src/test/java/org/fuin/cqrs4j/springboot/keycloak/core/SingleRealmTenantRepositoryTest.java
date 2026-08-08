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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link SingleRealmTenantRepository}.
 * <p>
 * Only the admission decision is exercised - resolving the tenant itself performs OIDC discovery and needs
 * a running Keycloak, which {@code KeycloakOutageIT} covers.
 */
class SingleRealmTenantRepositoryTest {

    private static final String CONFIGURED = "http://localhost:8082/realms/admin";

    /**
     * The point of this repository: a realm of the same Keycloak instance that is not the configured one
     * must be refused. {@link KeycloakTenantRepository} would discover and accept it.
     */
    @Test
    void testAnotherRealmOfTheSameInstanceIsRejected() {

        // PREPARE
        final SingleRealmTenantRepository testee = new SingleRealmTenantRepository(CONFIGURED);

        // TEST & VERIFY - empty, not "unknown, let me discover it"
        assertThat(testee.findByIssuer("http://localhost:8082/realms/other")).isEmpty();
        assertThat(testee.findByIssuer("http://localhost:8082/realms/master")).isEmpty();

    }

    @Test
    void testAnIssuerOfAnotherInstanceIsRejected() {

        // PREPARE
        final SingleRealmTenantRepository testee = new SingleRealmTenantRepository(CONFIGURED);

        // TEST & VERIFY
        assertThat(testee.findByIssuer("http://evil.example.com/realms/admin")).isEmpty();

    }

    @Test
    void testTheConfiguredIssuerIsExposed() {

        // PREPARE & TEST
        final SingleRealmTenantRepository testee = new SingleRealmTenantRepository(CONFIGURED);

        // VERIFY
        assertThat(testee.getIssuerUri()).isEqualTo(CONFIGURED);

    }

    @Test
    void testAnEmptyIssuerIsRejected() {

        // TEST & VERIFY
        assertThatThrownBy(() -> new SingleRealmTenantRepository("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuerUri");

    }

    @Test
    void testNoDiscoveryHappensWhileTheContextStarts() {

        // TEST: constructing must not contact Keycloak - there is none here, so an eager
        // implementation would fail right away
        final SingleRealmTenantRepository testee = new SingleRealmTenantRepository(CONFIGURED);

        // VERIFY
        assertThat(testee).isNotNull();

    }

}
