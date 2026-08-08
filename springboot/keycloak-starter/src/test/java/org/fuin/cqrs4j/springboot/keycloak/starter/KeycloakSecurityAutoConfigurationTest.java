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
package org.fuin.cqrs4j.springboot.keycloak.starter;

import org.fuin.cqrs4j.springboot.keycloak.core.JwtAudiencesValidator;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.SingleRealmTenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link KeycloakSecurityAutoConfiguration}.
 * <p>
 * The tenant repository resolves realms lazily, so none of these needs a running Keycloak.
 */
class KeycloakSecurityAutoConfigurationTest {

    private static final String ISSUER_URI = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/master";

    private static final String AUDIENCES = "spring.security.oauth2.resourceserver.jwt.audiences=melkheftken-api";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KeycloakSecurityAutoConfiguration.class));

    /**
     * The point of the fail-fast: an application that adds the starter but never configures an audience
     * must not silently come up accepting every token of every client of every accepted realm.
     */
    @Test
    void testMissingAudienceFailsStartup() {
        runner.withPropertyValues(ISSUER_URI)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("spring.security.oauth2.resourceserver.jwt.audiences"));
    }

    @Test
    void testBlankAudienceFailsStartup() {
        runner.withPropertyValues(ISSUER_URI, "spring.security.oauth2.resourceserver.jwt.audiences=  ")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("spring.security.oauth2.resourceserver.jwt.audiences"));
    }

    @Test
    void testConfiguredAudienceStartsUp() {
        runner.withPropertyValues(ISSUER_URI, AUDIENCES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBean(JwtAudiencesValidator.class).getExpectedAudiences())
                            .isEqualTo(Set.of("melkheftken-api"));
                });
    }

    @Test
    void testSeveralAudiencesAreAccepted() {
        runner.withPropertyValues(ISSUER_URI,
                        "spring.security.oauth2.resourceserver.jwt.audiences=command-api,query-api")
                .run(context -> assertThat(context.getBean(JwtAudiencesValidator.class).getExpectedAudiences())
                        .isEqualTo(Set.of("command-api", "query-api")));
    }

    /**
     * The decoder used to inject {@code OAuth2TokenValidator<Jwt>} by type, so an application adding a
     * validator of its own made the injection ambiguous and the context failed to start - which is what
     * kept the audience validator from being added in the first place.
     */
    @Test
    void testApplicationCanAddItsOwnValidator() {
        runner.withPropertyValues(ISSUER_URI, AUDIENCES)
                .withUserConfiguration(ExtraValidatorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBeansOfType(OAuth2TokenValidator.class)).hasSizeGreaterThanOrEqualTo(3);
                });
    }

    /**
     * The seam a control plane relies on: an application that supplies its own tenant repository replaces
     * the realm-discovering one, and with it the "every realm of this Keycloak is a tenant" trust boundary.
     */
    @Test
    void testAnApplicationCanReplaceTheTenantRepository() {
        runner.withPropertyValues(ISSUER_URI, AUDIENCES)
                .withUserConfiguration(SingleRealmConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtTenantRepository.class);
                    assertThat(context.getBean(JwtTenantRepository.class))
                            .isInstanceOf(SingleRealmTenantRepository.class);
                    assertThat(context).doesNotHaveBean(KeycloakTenantRepository.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class SingleRealmConfiguration {

        @Bean
        public JwtTenantRepository singleRealmTenantRepository() {
            return new SingleRealmTenantRepository("http://localhost:8080/realms/admin");
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class ExtraValidatorConfiguration {

        @Bean
        public OAuth2TokenValidator<Jwt> applicationValidator() {
            return token -> OAuth2TokenValidatorResult.success();
        }

    }

}
