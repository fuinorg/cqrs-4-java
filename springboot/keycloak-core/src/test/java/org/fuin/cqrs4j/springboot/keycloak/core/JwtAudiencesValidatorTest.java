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
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link JwtAudiencesValidator}.
 */
class JwtAudiencesValidatorTest {

    @Test
    void testMatchingAudiencePasses() {

        // PREPARE
        final JwtAudiencesValidator testee = new JwtAudiencesValidator(List.of("melkheftken-api"));

        // TEST
        final OAuth2TokenValidatorResult result = testee.validate(jwt(List.of("melkheftken-api")));

        // VERIFY
        assertThat(result.hasErrors()).isFalse();

    }

    @Test
    void testOneOfSeveralExpectedAudiencesPasses() {

        // PREPARE
        final JwtAudiencesValidator testee = new JwtAudiencesValidator(List.of("command-api", "query-api"));

        // TEST
        final OAuth2TokenValidatorResult result = testee.validate(jwt(List.of("query-api")));

        // VERIFY
        assertThat(result.hasErrors()).isFalse();

    }

    @Test
    void testOneOfSeveralTokenAudiencesPasses() {

        // PREPARE
        final JwtAudiencesValidator testee = new JwtAudiencesValidator(List.of("melkheftken-api"));

        // TEST
        final OAuth2TokenValidatorResult result = testee.validate(jwt(List.of("account", "melkheftken-api")));

        // VERIFY
        assertThat(result.hasErrors()).isFalse();

    }

    /**
     * The hole this validator exists to close: Keycloak puts "account" into "aud" by default, so a token
     * minted for an unrelated client of the same realm must not be accepted.
     */
    @Test
    void testForeignAudienceFails() {

        // PREPARE
        final JwtAudiencesValidator testee = new JwtAudiencesValidator(List.of("melkheftken-api"));

        // TEST
        final OAuth2TokenValidatorResult result = testee.validate(jwt(List.of("account")));

        // VERIFY
        assertThat(result.hasErrors()).isTrue();

    }

    @Test
    void testMissingAudienceClaimFails() {

        // PREPARE
        final JwtAudiencesValidator testee = new JwtAudiencesValidator(List.of("melkheftken-api"));

        // TEST
        final OAuth2TokenValidatorResult result = testee.validate(jwt(null));

        // VERIFY
        assertThat(result.hasErrors()).isTrue();

    }

    @Test
    void testEmptyExpectedAudiencesRejected() {

        // TEST & VERIFY
        assertThatThrownBy(() -> new JwtAudiencesValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedAudiences");

    }

    @Test
    void testExpectedAudiencesAreExposed() {

        // PREPARE
        final JwtAudiencesValidator testee = new JwtAudiencesValidator(List.of("a", "b"));

        // TEST & VERIFY
        assertThat(testee.getExpectedAudiences()).isEqualTo(Set.of("a", "b"));

    }

    private static Jwt jwt(final List<String> audiences) {
        final Map<String, Object> claims = audiences == null
                ? Map.of("sub", "peter")
                : Map.of("sub", "peter", "aud", audiences);
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

}
