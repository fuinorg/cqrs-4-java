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
import org.fuin.cqrs4j.springboot.keycloak.core.JwtAudiencesValidator;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantIssuerValidator;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantKeySelector;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakJwtAuthenticationConverter;
import org.fuin.cqrs4j.springboot.command.core.CommandExecutionContextProvider;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakCommandExecutionContextProvider;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.beans.factory.ObjectProvider;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Auto-configuration that wires the multi-tenant Keycloak/JWT security infrastructure. Adding this
 * starter and setting the two properties below is enough to get a tenant-aware {@link JwtDecoder} and the
 * supporting beans without configuring them by hand. Every bean is conditional, so an application can
 * override any of them by declaring its own.
 * <p>
 * Required configuration:
 * <ul>
 * <li>{@code spring.security.oauth2.resourceserver.jwt.issuer-uri} - the Keycloak <b>master</b> realm.
 * Every realm below its base URI is a tenant.</li>
 * <li>{@code spring.security.oauth2.resourceserver.jwt.audiences} - the audience(s) a token must carry at
 * least one of. <b>The context refuses to start without it</b>, because a resource server that does not
 * check {@code aud} accepts every token of every client of every accepted realm. The Keycloak client
 * needs a matching audience mapper, otherwise its tokens carry only the default {@code account}
 * audience and will be rejected here.</li>
 * </ul>
 * <p>
 * The {@link JwtDecoder} applies the default validators (signature, expiry) plus <b>every</b>
 * {@link OAuth2TokenValidator} bean of the context - the tenant issuer validator and the audience
 * validator declared here, and any the application adds itself.
 */
@ThreadSafe
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
     * Validator that asserts the token was issued for this resource server.
     *
     * @param audiences Comma separated audiences a token must carry at least one of, from
     *                  {@code spring.security.oauth2.resourceserver.jwt.audiences}. Split here rather
     *                  than bound as a {@code List}, because that binding silently yields a single
     *                  element unless a splitting conversion service happens to be registered.
     *
     * @return New validator instance.
     *
     * @throws IllegalStateException No audience is configured.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAudiencesValidator jwtAudiencesValidator(
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences:}") final String audiences) {
        final List<String> configured = Arrays.stream(audiences.split(","))
                .map(String::trim)
                .filter(aud -> !aud.isEmpty())
                .toList();
        if (configured.isEmpty()) {
            throw new IllegalStateException(
                    "No audience configured. Set 'spring.security.oauth2.resourceserver.jwt.audiences' to the "
                            + "audience this resource server expects - without it every token issued for any "
                            + "client of any accepted realm is valid here. The Keycloak client needs an audience "
                            + "mapper emitting that value.");
        }
        return new JwtAudiencesValidator(configured);
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
     * @param jwtValidators All token validators of the context - the tenant issuer validator and the
     *                      audience validator declared here, plus any the application added. They are
     *                      applied on top of the defaults (signature, expiry), all of them having to
     *                      pass.
     * @param tenantContext Optional writable tenant context to populate.
     * @return New decoder instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(final JWTProcessor<SecurityContext> jwtProcessor,
                                 final ObjectProvider<OAuth2TokenValidator<Jwt>> jwtValidators,
                                 final Optional<WritableTenantContext> tenantContext) {

        final NimbusJwtDecoder nimbus = new NimbusJwtDecoder(jwtProcessor);
        final List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefault());
        jwtValidators.orderedStream().forEach(validators::add);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));

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


    /**
     * Creates the provider that derives the command execution context from the Keycloak token of the
     * current request. It supersedes the default provider of the command starter, which only knows
     * the authenticated name and no realm/tenant.
     *
     * @return Execution context provider.
     */
    @Bean
    @ConditionalOnMissingBean(CommandExecutionContextProvider.class)
    public CommandExecutionContextProvider keycloakCommandExecutionContextProvider() {
        return new KeycloakCommandExecutionContextProvider();
    }

}
