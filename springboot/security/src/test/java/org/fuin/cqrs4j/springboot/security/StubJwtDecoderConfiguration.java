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

package org.fuin.cqrs4j.springboot.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A decoder that recognises three fixed token values, for the tests that are about <b>authorization</b>
 * and should not pay for a Keycloak container.
 *
 * <h2>Why this is a {@code @TestConfiguration} and not a bean of the test application</h2>
 * <p>
 * It used to be one, and that silently broke {@link ApiSecurityIT}: a {@code @Bean} inside the
 * {@code @SpringBootApplication} is found by component scan, and the keycloak starter's real decoder is
 * {@code @ConditionalOnMissingBean}, so the stub replaced it in <em>every</em> test - including the one
 * whose entire purpose is to verify real Keycloak tokens. It failed with
 * {@code "Not a token this test issued"} while looking like an audience problem.
 * <p>
 * A {@code @TestConfiguration} is excluded from component scan and has to be asked for by name, so the
 * integration test cannot pick it up by accident.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubJwtDecoderConfiguration {

    /** Token value mapping to a caller holding no role at all. */
    public static final String TOKEN_NO_ROLES = "token-without-roles";

    /** Token value mapping to a caller holding {@code tenant-admin}. */
    public static final String TOKEN_ADMIN = "token-of-an-admin";

    /** Token value mapping to a caller holding {@code svc-tenant-read}. */
    public static final String TOKEN_READER = "token-of-a-reader";

    /**
     * Decodes the three fixed token values above and rejects everything else.
     *
     * @return Decoder recognising exactly the tokens these tests hand out.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> switch (token) {
            case TOKEN_NO_ROLES -> jwt(token, List.of());
            case TOKEN_ADMIN -> jwt(token, List.of("tenant-admin"));
            case TOKEN_READER -> jwt(token, List.of("svc-tenant-read"));
            // BadJwtException and not a plain JwtException: the resource server maps the former to
            // 'invalid_token' and a 401, and the latter to an AuthenticationServiceException, which is
            // a 500. A real decoder throws the former for a token it will not accept.
            default -> throw new BadJwtException("Not a token this test issued: " + token);
        };
    }

    private static Jwt jwt(final String value, final List<String> roles) {
        final Jwt.Builder builder = Jwt.withTokenValue(value)
                .header("alg", "RS256")
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (!roles.isEmpty()) {
            // Keycloak omits the claim entirely for a user with no realm role, which is exactly the
            // case the empty list reproduces.
            builder.claim("realm_access", Map.of("roles", roles));
        }
        return builder.build();
    }

}
