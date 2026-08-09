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

import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakJwtAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.ClassUtils;

/**
 * The filter chain a CQRS application serves its API behind, built from
 * {@link Cqrs4jSecurityProperties}.
 * <p>
 * Adding this artifact and setting {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} and
 * {@code ...jwt.audiences} is the whole of it: sessions are <b>stateless</b>, CSRF is off, health and
 * info are open, and everything else needs a valid bearer token. Role rules are YAML.
 * <p>
 * The chain is {@link ConditionalOnMissingBean}, so an application that declares its own
 * {@code SecurityFilterChain} - a permit-all one under a development profile, or a test chain - replaces
 * this whole.
 *
 * <h2>Why this is not in the keycloak starter</h2>
 * <p>
 * That one validates tokens; this one decides access. Keeping them apart means the keycloak starter
 * needs no dependency on {@code spring-security-config} or {@code -web}, and an application that wants
 * only token validation is unaffected by this existing.
 *
 * <h2>Three things that are easy to get wrong, and are handled here once</h2>
 * <ul>
 * <li><b>Ordering.</b> Registered {@code before} {@link SecurityAutoConfiguration} <b>and</b>
 * {@link OAuth2ResourceServerAutoConfiguration}. Both of Boot's own chains are
 * {@code @ConditionalOnDefaultWebSecurity}, which is {@code @ConditionalOnMissingBean(SecurityFilterChain)},
 * so they only back off if they see this one already registered. Miss either and Boot's chain wins.</li>
 * <li><b>The converter.</b> {@link KeycloakJwtAuthenticationConverter} is handed to {@code jwt(...)}
 * <b>explicitly</b>. {@code OAuth2ResourceServerConfigurer} looks up a bean of the concrete class
 * {@code JwtAuthenticationConverter}, which this one is not, so leaving it to the default grants no
 * {@code ROLE_} authority at all. Nothing fails until the first role check, which then looks like a
 * Keycloak misconfiguration.</li>
 * <li><b>Health.</b> {@code ManagementWebSecurityAutoConfiguration} is what normally permits it, and its
 * javadoc is explicit that it <i>"will back-off completely"</i> once an application declares any chain.
 * Declaring one therefore silently closes health unless it is permitted again - which is what
 * {@code permit-actuator} does, through {@link EndpointRequest} rather than a literal path so a moved
 * actuator base path keeps working.</li>
 * </ul>
 *
 * <h2>The escape hatch is Java, on purpose</h2>
 * <p>
 * {@link Cqrs4jSecurityProperties} cannot express "open this path". An application that needs to declares
 * a {@code Customizer} bean, which is applied after the configured rules and before the
 * {@code anyRequest()} default:
 *
 * <pre>
 * &#64;Bean
 * Customizer&lt;AuthorizeHttpRequestsConfigurer&lt;HttpSecurity&gt;.AuthorizationManagerRequestMatcherRegistry&gt;
 *         publicPaths() {
 *     return auth -&gt; auth.requestMatchers("/public/**").permitAll();
 * }
 * </pre>
 * <p>
 * That is more work than a YAML line, and that is the point: it lands in the application's own source
 * tree where the ArchUnit rule in {@code cqrs-4-java-test-helper} can see it and where a reviewer reads
 * it as security code.
 */
@AutoConfiguration(before = { SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ SecurityFilterChain.class, HttpSecurity.class })
@EnableConfigurationProperties(Cqrs4jSecurityProperties.class)
public class ApiSecurityAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ApiSecurityAutoConfiguration.class);

    private static final String ENDPOINT_REQUEST =
            "org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest";

    /**
     * Creates the one filter chain of a deployable.
     *
     * @param http Chain being built.
     * @param properties What the chain enforces.
     * @param jwtDecoder Tenant-aware decoder, normally the keycloak starter's. Passed explicitly rather
     *                   than looked up by the configurer, so a missing or ambiguous decoder fails the
     *                   context with a readable message instead of silently accepting nothing.
     * @param authenticationConverter Maps the token's realm roles to {@code ROLE_}-prefixed authorities.
     * @param customizers Application-supplied additions, applied after the configured rules and before
     *                    the {@code authenticated()} default.
     *
     * @return The chain.
     *
     * @throws Exception Building the chain failed.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain apiSecurityFilterChain(final HttpSecurity http,
            final Cqrs4jSecurityProperties properties, final JwtDecoder jwtDecoder,
            final KeycloakJwtAuthenticationConverter authenticationConverter,
            final ObjectProvider<Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry>> customizers) throws Exception {

        // Logged at INFO because it is the authorization model of a running instance in one line, and
        // the only way to tell from outside what a deployment actually enforces.
        LOG.info("API secured - {}", properties.describe());

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (properties.permitActuator()) {
                        permitActuator(auth);
                    }
                    for (final Cqrs4jSecurityProperties.Rule rule : properties.rules()) {
                        auth.requestMatchers(rule.paths().toArray(new String[0]))
                                .hasAnyRole(rule.hasAnyRole().toArray(new String[0]));
                    }
                    customizers.orderedStream().forEach(customizer -> customizer.customize(auth));
                    // Always last, and never configurable: whatever nobody claimed still needs a token.
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                    jwt.decoder(jwtDecoder);
                    jwt.jwtAuthenticationConverter(authenticationConverter);
                }))
                .build();
    }

    /**
     * Permits health and info, if the actuator is on the class path.
     * <p>
     * Checked at runtime rather than with {@code @ConditionalOnClass} on the bean method, because the
     * rest of the chain must still be created for an application that has no actuator. Referencing
     * {@link EndpointRequest} only from inside this method keeps the class loadable without it.
     */
    private static void permitActuator(final AuthorizeHttpRequestsConfigurer<HttpSecurity>
            .AuthorizationManagerRequestMatcherRegistry auth) {
        if (!ClassUtils.isPresent(ENDPOINT_REQUEST, ApiSecurityAutoConfiguration.class.getClassLoader())) {
            LOG.warn("'{}.permit-actuator' is on, but spring-boot-actuator is not on the class path - "
                    + "there is nothing to permit.", Cqrs4jSecurityProperties.PREFIX);
            return;
        }
        auth.requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll();
    }

}
