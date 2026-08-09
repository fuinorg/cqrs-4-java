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

import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.SingleRealmTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.starter.KeycloakSecurityAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Pins the trust boundary to the one realm the application serves.
 *
 * <h2>Why this is the default</h2>
 * <p>
 * Without it the keycloak starter auto-configures {@link KeycloakTenantRepository}, which discovers
 * realms <b>on demand</b> and accepts every one of them whose issuer starts with the configured base
 * URI - <em>regardless of the multitenancy flag</em>. That is no admission control at all and no
 * revocation ever: a realm it once accepted stays accepted until the application restarts. For an
 * application that is not multi-tenant, which is most of them on day one, that is a trust boundary
 * nobody chose.
 * <p>
 * Set {@code cqrs4j.security.tenants: discover} to get the discovering repository back, or declare a
 * {@link JwtTenantRepository} bean of your own - jtenman's replicated tenant list is one - which this
 * backs off from either way.
 * <p>
 * Ordered {@code before} {@link KeycloakSecurityAutoConfiguration}, whose own repository bean is
 * {@code @ConditionalOnMissingBean(JwtTenantRepository.class)} and therefore has to see this one already
 * registered to stand down. Get the order wrong and the context fails on an ambiguous injection into the
 * issuer validator - loudly, but wrong all the same.
 */
@AutoConfiguration(before = KeycloakSecurityAutoConfiguration.class)
@ConditionalOnProperty(prefix = Cqrs4jSecurityProperties.PREFIX, name = "tenants",
        havingValue = "single-realm", matchIfMissing = true)
@EnableConfigurationProperties(Cqrs4jSecurityProperties.class)
public class SingleRealmTenantAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SingleRealmTenantAutoConfiguration.class);

    /**
     * Creates the repository that accepts exactly one issuer.
     *
     * @param issuerUri The realm this application serves.
     *
     * @return Repository holding that one realm.
     */
    @Bean
    @ConditionalOnMissingBean(JwtTenantRepository.class)
    public SingleRealmTenantRepository singleRealmTenantRepository(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String issuerUri) {
        // Logged at INFO because it is the whole trust boundary in one line, and the only way to tell
        // from a running instance which realm it actually accepts.
        LOG.info("Tenant trust boundary pinned to the single issuer '{}' - every other realm of this "
                + "Keycloak instance is rejected", issuerUri);
        return new SingleRealmTenantRepository(issuerUri);
    }

}
