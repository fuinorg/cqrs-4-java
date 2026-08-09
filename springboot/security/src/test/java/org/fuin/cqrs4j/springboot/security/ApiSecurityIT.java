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

import org.fuin.cqrs4j.test.helper.KeycloakRealm;
import org.fuin.cqrs4j.test.helper.TestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole thing against a real Keycloak: real signatures, real audiences, real realm roles.
 *
 * <h2>Why this lives here and not only in the applications using it</h2>
 * <p>
 * Two reasons. The chain is now shared, so proving it once here means an application inherits proven
 * behaviour instead of reproving it - which is the entire promise of "add the dependency and write
 * YAML". And {@link KeycloakRealm} is shipped for other projects to build their own tests on: a fixture
 * nobody exercises is a fixture that quietly stops working, and the failure would surface in somebody
 * else's repository.
 * <p>
 * {@link ApiSecurityAutoConfigurationTest} beside this covers the same authorization model with a stub
 * decoder and no container, which is where most of the cases belong. What can only be shown here is the
 * pair a stub cannot fake: a token that is <b>correctly signed by the trusted realm and meant for
 * somebody else</b> being refused, and one with the right audience being accepted.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.audiences=" + KeycloakRealm.AUDIENCE,
                "management.endpoints.web.exposure.include=health,info",
                "cqrs4j.security.rules[0].paths=/cmd/**",
                "cqrs4j.security.rules[0].has-any-role=tenant-admin" })
class ApiSecurityIT {

    /** Same image and version a developer's compose file uses, so the image is already local. */
    private static final String KEYCLOAK_VERSION = "26.0.7";

    /** A tenant realm - never Keycloak's own 'master', which is where its administrators live. */
    private static final String REALM = "acme";

    /** Has the audience and the role. */
    private static final String ADMIN_CLIENT = "an-admin";

    @Container
    @SuppressWarnings("resource") // Testcontainers closes it
    static final GenericContainer<?> KEYCLOAK = TestHelper.createKeycloakContainer(KEYCLOAK_VERSION);

    private static KeycloakRealm keycloak;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Points the application at the container.
     * <p>
     * A supplier, because this runs before Testcontainers has necessarily started anything and the
     * mapped port only exists afterwards.
     *
     * @param registry Registry the properties are added to.
     */
    @DynamicPropertySource
    static void containerProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> TestHelper.keycloakUrl(KEYCLOAK) + "/realms/" + REALM);
    }

    @BeforeAll
    static void provisionRealm() {
        keycloak = new KeycloakRealm(TestHelper.keycloakUrl(KEYCLOAK), REALM);
        keycloak.provision();
        // A third client, so the 403 case has a caller that is known and simply not allowed - as
        // opposed to one whose token is refused outright.
        keycloak.createServiceAccountClient(ADMIN_CLIENT, true);
        keycloak.grantRole(ADMIN_CLIENT, "tenant-admin");
    }

    @Test
    void testWithoutATokenEverythingIsRefused() {

        assertThat(get("/something-else", null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/cmd/anything", null).getStatusCode().value()).isEqualTo(401);

    }

    /**
     * The case only a real issuer can show: correctly signed by the realm this server trusts, and issued
     * for somebody else. Without the audience check it would be accepted, which is why the keycloak
     * starter refuses to start unless an audience is configured.
     */
    @Test
    void testATokenForAnotherAudienceIsRefused() {

        final String token = keycloak.tokenFor(KeycloakRealm.CLIENT_WITHOUT_AUDIENCE);

        assertThat(get("/something-else", token).getStatusCode().value()).isEqualTo(401);

    }

    @Test
    void testATokenWithTheRightAudienceIsAccepted() {

        final String token = keycloak.tokenFor(KeycloakRealm.CLIENT_WITH_AUDIENCE);

        assertThat(get("/something-else", token).getStatusCode().value()).isEqualTo(200);

    }

    /**
     * The configured rule, end to end - including that a realm role granted through a group actually
     * arrives as a {@code ROLE_} authority. That is the part the explicit
     * {@code KeycloakJwtAuthenticationConverter} in the chain exists for; left to Spring's default it
     * would grant no authority at all and this would be a 403 for everyone.
     */
    @Test
    void testARoleRuleIsEnforcedAgainstRealRealmRoles() {

        assertThat(get("/cmd/anything", keycloak.tokenFor(ADMIN_CLIENT)).getStatusCode().value())
                .isEqualTo(200);
        // Authenticated and refused, which is a 403 and not a 401.
        assertThat(get("/cmd/anything", keycloak.tokenFor(KeycloakRealm.CLIENT_WITH_AUDIENCE))
                .getStatusCode().value()).isEqualTo(403);

    }

    @Test
    void testHealthIsReachableWithoutAToken() {

        final ResponseEntity<String> response = get("/actuator/health", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");

    }

    private ResponseEntity<String> get(final String path, final String token) {
        final HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

}
