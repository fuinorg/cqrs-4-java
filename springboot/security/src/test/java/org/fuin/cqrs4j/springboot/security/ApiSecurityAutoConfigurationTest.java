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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the YAML actually enforces, asked of a running chain.
 * <p>
 * The promise of this module is "add the dependency, write a few properties, done". That promise is only
 * worth anything if the properties provably become the behaviour, so these tests drive real requests
 * through the real filter chain rather than inspecting the configuration object.
 * <p>
 * The token decoder is a stub - see {@link SecurityTestApplication}. Signature verification belongs to
 * the keycloak starter and is tested there; what is under test here is authorization, which needs no
 * Keycloak.
 */
class ApiSecurityAutoConfigurationTest {

    private static final String ISSUER = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/test";

    private static final String AUDIENCE = "spring.security.oauth2.resourceserver.jwt.audiences=test-api";

    /**
     * Boot exposes only {@code health} over HTTP by default. An unexposed endpoint is not matched by
     * {@code EndpointRequest} at all, so it falls through to the {@code authenticated()} default and
     * answers 401 - which looks like the permit failing when it is really the endpoint being absent.
     */
    private static final String EXPOSE = "management.endpoints.web.exposure.include=health,info";

    /**
     * The default posture: no rules configured at all.
     * <p>
     * This is what a new project gets from the dependency alone, and it is the case worth being most
     * confident about - everything closed except the two endpoints an orchestrator probes.
     */
    @Nested
    @SpringBootTest(properties = { ISSUER, AUDIENCE, EXPOSE })
    @AutoConfigureMockMvc
    @Import(StubJwtDecoderConfiguration.class)
    class WithNoRulesConfigured {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void testEverythingNeedsAToken() throws Exception {
            mockMvc.perform(get("/cmd/anything")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/view/anything")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/something-else")).andExpect(status().isUnauthorized());
        }

        @Test
        void testAnyValidTokenIsEnough() throws Exception {
            mockMvc.perform(get("/cmd/anything").header("Authorization",
                    "Bearer " + StubJwtDecoderConfiguration.TOKEN_NO_ROLES)).andExpect(status().isOk());
        }

        @Test
        void testAnUnknownTokenIsRefused() throws Exception {
            mockMvc.perform(get("/cmd/anything").header("Authorization", "Bearer not-a-token"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Open by default, because a container orchestrator's probe carries no token - and because
         * declaring any chain makes Boot's own actuator security back off completely, so health closes
         * unless something permits it again.
         */
        @Test
        void testHealthAndInfoAreOpen() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
        }
    }

    /**
     * Role rules, in the shape a control plane needs them.
     */
    @Nested
    @SpringBootTest(properties = { ISSUER, AUDIENCE, EXPOSE,
            "cqrs4j.security.rules[0].paths=/cmd/**",
            "cqrs4j.security.rules[0].has-any-role=tenant-admin",
            "cqrs4j.security.rules[1].paths=/view/**",
            "cqrs4j.security.rules[1].has-any-role=tenant-admin,svc-tenant-read" })
    @AutoConfigureMockMvc
    @Import(StubJwtDecoderConfiguration.class)
    class WithRoleRules {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void testARuleRequiresItsRole() throws Exception {
            mockMvc.perform(get("/cmd/anything").header("Authorization",
                    "Bearer " + StubJwtDecoderConfiguration.TOKEN_ADMIN)).andExpect(status().isOk());
            // Authenticated, and still refused - which is a 403 and not a 401, because the caller is
            // known and simply not allowed.
            mockMvc.perform(get("/cmd/anything").header("Authorization",
                    "Bearer " + StubJwtDecoderConfiguration.TOKEN_READER)).andExpect(status().isForbidden());
        }

        @Test
        void testAnyOfTheListedRolesSatisfiesARule() throws Exception {
            mockMvc.perform(get("/view/anything").header("Authorization",
                    "Bearer " + StubJwtDecoderConfiguration.TOKEN_ADMIN)).andExpect(status().isOk());
            mockMvc.perform(get("/view/anything").header("Authorization",
                    "Bearer " + StubJwtDecoderConfiguration.TOKEN_READER)).andExpect(status().isOk());
        }

        /**
         * A path no rule mentions still needs a token, and needs nothing more. That default is not
         * configurable, which is what stops a forgotten rule from opening anything.
         */
        @Test
        void testAnUnmatchedPathFallsBackToAuthenticated() throws Exception {
            mockMvc.perform(get("/something-else")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/something-else").header("Authorization",
                    "Bearer " + StubJwtDecoderConfiguration.TOKEN_NO_ROLES)).andExpect(status().isOk());
        }

        @Test
        void testRulesDoNotOpenHealth() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    /**
     * Closing health is a deliberate act, so it has to be possible - and visible.
     */
    @Nested
    @SpringBootTest(properties = { ISSUER, AUDIENCE, EXPOSE, "cqrs4j.security.permit-actuator=false" })
    @AutoConfigureMockMvc
    @Import(StubJwtDecoderConfiguration.class)
    class WithTheActuatorClosed {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void testHealthNeedsATokenToo() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/actuator/health").header("Authorization",
                    "Bearer " + StubJwtDecoderConfiguration.TOKEN_NO_ROLES)).andExpect(status().isOk());
        }
    }

}
