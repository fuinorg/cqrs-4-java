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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link KeycloakJwtAuthenticationConverter}.
 */
class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter testee = new KeycloakJwtAuthenticationConverter();

    @Test
    void testRealmRolesBecomeAuthorities() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("user", "group-admin"))));

        // TEST
        final AbstractAuthenticationToken result = testee.convert(jwt);

        // VERIFY
        assertThat(authorities(result)).containsExactlyInAnyOrder("ROLE_user", "ROLE_group-admin");

    }

    @Test
    void testScopesAreKeptBesideRealmRoles() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("scope", "openid profile",
                "realm_access", Map.of("roles", List.of("user"))));

        // TEST
        final AbstractAuthenticationToken result = testee.convert(jwt);

        // VERIFY
        assertThat(authorities(result)).containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_profile", "ROLE_user");

    }

    /**
     * Keycloak omits the claim completely for a user without any realm role. This must not fail - it used
     * to cause a {@link NullPointerException} and therefore a 500 instead of an authenticated caller
     * without roles.
     */
    @Test
    void testMissingRealmAccessClaimYieldsNoRoles() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("scope", "openid"));

        // TEST
        final AbstractAuthenticationToken result = testee.convert(jwt);

        // VERIFY
        assertThat(authorities(result)).containsExactly("SCOPE_openid");

    }

    @Test
    void testRealmAccessWithoutRolesYieldsNoRoles() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("realm_access", Map.of("something", "else")));

        // TEST
        final AbstractAuthenticationToken result = testee.convert(jwt);

        // VERIFY
        assertThat(authorities(result)).isEmpty();

    }

    @Test
    void testEmptyRoleListYieldsNoRoles() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of())));

        // TEST
        final AbstractAuthenticationToken result = testee.convert(jwt);

        // VERIFY
        assertThat(authorities(result)).isEmpty();

    }

    @Test
    void testRealmAccessOfWrongTypeIsRejected() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("realm_access", "not-a-map"));

        // TEST & VERIFY
        assertThatThrownBy(() -> testee.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("realm_access");

    }

    @Test
    void testRolesOfWrongTypeIsRejected() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", "user")));

        // TEST & VERIFY
        assertThatThrownBy(() -> testee.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("realm_access.roles");

    }

    @Test
    void testNonStringRoleIsRejected() {

        // PREPARE
        final Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of(1))));

        // TEST & VERIFY
        assertThatThrownBy(() -> testee.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("realm_access.roles");

    }

    private static Jwt jwt(final Map<String, Object> claims) {
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    private static Iterable<String> authorities(final AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(toSet());
    }

}
