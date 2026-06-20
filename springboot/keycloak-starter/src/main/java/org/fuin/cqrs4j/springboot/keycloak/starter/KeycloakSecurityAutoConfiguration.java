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

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector;
import com.nimbusds.jwt.proc.JWTProcessor;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantIssuerValidator;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantKeySelector;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakJwtAuthenticationConverter;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Optional;

/**
 * Auto-configuration that wires the multi-tenant Keycloak/JWT security infrastructure. Adding this
 * starter and setting {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (pointing at the
 * Keycloak master realm) is enough to get a tenant-aware {@link JwtDecoder} and the supporting beans
 * without configuring them by hand. Every bean is conditional, so an application can override any of
 * them by declaring its own.
 */
@AutoConfiguration
public class KeycloakSecurityAutoConfiguration {

    /**
     * Tenant repository backed by the Keycloak master realm.
     *
     * @param masterIssuerUri Issuer URI of the Keycloak master realm.
     * @param publisher       Used to publish tenant added/removed events.
     * @return New repository instance.
     */
    @Bean
    @ConditionalOnMissingBean(JwtTenantRepository.class)
    public KeycloakTenantRepository keycloakTenantRepository(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String masterIssuerUri,
            final ApplicationEventPublisher publisher) {
        return new KeycloakTenantRepository(masterIssuerUri, publisher);
    }

    /**
     * Validator that asserts the token issuer belongs to a known tenant.
     *
     * @param tenantRepository Repository with the known tenants.
     * @return New validator instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTenantIssuerValidator tenantJwtIssuerValidator(final JwtTenantRepository tenantRepository) {
        return new JwtTenantIssuerValidator(tenantRepository);
    }

    /**
     * Key selector that picks the signing keys based on the tenant of the token.
     *
     * @param tenantRepository Repository with the known tenants.
     * @return New key selector instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTenantKeySelector tenantJwtKeySelector(final JwtTenantRepository tenantRepository) {
        return new JwtTenantKeySelector(tenantRepository);
    }

    /**
     * Nimbus JWT processor using the tenant-aware key selector.
     *
     * @param keySelector Tenant-aware key selector.
     * @return New processor instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public JWTProcessor<SecurityContext> jwtProcessor(final JWTClaimsSetAwareJWSKeySelector<SecurityContext> keySelector) {
        final ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWTClaimsSetAwareJWSKeySelector(keySelector);
        return jwtProcessor;
    }

    /**
     * Tenant-aware JWT decoder. Beside decoding and validating the token it extracts the realm from
     * the issuer and stores it as the current tenant in the {@link WritableTenantContext} (if present).
     *
     * @param jwtProcessor  Nimbus JWT processor.
     * @param jwtValidator  Additional validator (the tenant issuer validator).
     * @param tenantContext Optional writable tenant context to populate.
     * @return New decoder instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(final JWTProcessor<SecurityContext> jwtProcessor,
                                 final OAuth2TokenValidator<Jwt> jwtValidator,
                                 final Optional<WritableTenantContext> tenantContext) {

        final NimbusJwtDecoder nimbus = new NimbusJwtDecoder(jwtProcessor);
        final OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), jwtValidator);
        nimbus.setJwtValidator(validator);

        return token -> {
            final Jwt jwt = nimbus.decode(token);
            final String realm = extractRealm(jwt);
            tenantContext.ifPresent(tctx -> tctx.setTenantId(new TenantId(realm)));
            return jwt;
        };
    }

    /**
     * Converts a Keycloak JWT into an authentication token carrying the realm roles as authorities.
     *
     * @return New converter instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    private static String extractRealm(final Jwt jwt) {
        final java.net.URL issuer = jwt.getIssuer();
        if (issuer == null) {
            throw new IllegalArgumentException("The JWT 'iss' (issuer) claim must not be null");
        }
        final String iss = issuer.getPath();
        final int p = iss.lastIndexOf('/');
        if (p < 0) {
            throw new IllegalArgumentException("Failed to extract realm from 'iss': " + iss);
        }
        return iss.substring(p + 1);
    }

}
