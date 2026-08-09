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

import org.fuin.objects4j.common.Immutable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How an application's API is protected, as configuration.
 *
 * <pre>
 * cqrs4j:
 *   security:
 *     permit-actuator: true          # health and info; a named set, never a path
 *     tenants: single-realm          # or: discover
 *     rules:                         # ORDER MATTERS - first match wins, as in Spring Security
 *       - paths: [ "/cmd/**" ]
 *         has-any-role: [ tenant-admin ]
 *       - paths: [ "/view/**" ]
 *         has-any-role: [ tenant-admin, svc-tenant-read ]
 * </pre>
 *
 * <h2>Nothing here can open a path</h2>
 * <p>
 * A rule may require a role. It cannot permit. Anything not matched by a rule is
 * {@code authenticated()}, and that is not configurable either.
 * <p>
 * This is deliberate and it is the reason the shape looks restrictive. Until now, the guarantee that no
 * blanket permit-all reaches production came from an ArchUnit rule reading the <em>code</em> of the
 * filter chain. Moving the rules into YAML would defeat that guard entirely - a {@code permit: "/**"}
 * line is invisible to ArchUnit, to code review diffs of Java files, and to every test that inspects
 * classes. So the property model simply has no way to express it.
 * <p>
 * The one exception is {@link #permitActuator()}, and it is safe because it is a <b>named set of two
 * endpoints</b> rather than a path pattern: it cannot be widened into anything else, and it is matched
 * through {@code EndpointRequest} so it keeps working when the actuator base path moves.
 * <p>
 * An application that genuinely needs to open a path declares a {@code Customizer} bean - see
 * {@link ApiSecurityAutoConfiguration}. That is Java, in the application's own source tree, where the
 * ArchUnit rule can see it.
 */
@Immutable
@ConfigurationProperties(Cqrs4jSecurityProperties.PREFIX)
public record Cqrs4jSecurityProperties(
        @DefaultValue("true") boolean permitActuator,
        @DefaultValue("single-realm") Tenants tenants,
        @DefaultValue List<Rule> rules) {

    /** Prefix of all settings described here. */
    public static final String PREFIX = "cqrs4j.security";

    /** How the set of accepted realms is decided. */
    public enum Tenants {

        /**
         * Exactly one realm - the one named by {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
         * <p>
         * The default, and the right answer for any application that is not multi-tenant. Without it the
         * keycloak starter's own repository discovers realms on demand and accepts <b>every</b> realm of
         * the Keycloak instance, regardless of the multitenancy flag - which is no admission control at
         * all and no revocation ever.
         */
        SINGLE_REALM,

        /**
         * Leave the tenant repository to somebody else - the keycloak starter's discovering one, or an
         * application-supplied {@code JwtTenantRepository} such as jtenman's replicated tenant list.
         */
        DISCOVER
    }

    /**
     * One authorization rule.
     *
     * @param paths Ant-style patterns this rule applies to. Mandatory and non-empty.
     * @param hasAnyRole Realm roles, any one of which satisfies the rule. Mandatory and non-empty:
     *                   a rule that requires nothing would be a permit, which this model does not have.
     *                   Names are given <b>without</b> the {@code ROLE_} prefix, as Spring Security's
     *                   {@code hasAnyRole} expects.
     */
    @Immutable
    public record Rule(List<String> paths, List<String> hasAnyRole) {

        /**
         * Compact constructor validating both halves.
         */
        public Rule {
            paths = List.copyOf(Objects.requireNonNull(paths, "paths==null"));
            hasAnyRole = List.copyOf(Objects.requireNonNull(hasAnyRole, "hasAnyRole==null"));
            if (paths.isEmpty()) {
                throw new IllegalArgumentException("A '" + PREFIX + ".rules' entry has no 'paths'");
            }
            if (hasAnyRole.isEmpty()) {
                // Silently authenticating instead would look like a working rule while enforcing
                // nothing beyond the default - the exact failure this model exists to prevent.
                throw new IllegalArgumentException("The rule for " + paths + " has no 'has-any-role'. "
                        + "A rule must require a role; paths needing only a valid token are already "
                        + "covered by the default.");
            }
            hasAnyRole.forEach(role -> {
                if (role.startsWith("ROLE_")) {
                    // hasAnyRole adds the prefix itself, so 'ROLE_x' silently becomes 'ROLE_ROLE_x'
                    // and matches nothing.
                    throw new IllegalArgumentException("Role '" + role + "' must be given without the "
                            + "'ROLE_' prefix - Spring Security adds it.");
                }
            });
        }
    }

    /**
     * Compact constructor normalising the collection.
     *
     * @param permitActuator Whether the health and info endpoints answer without a token. Defaults to
     *                       {@literal true}: an orchestrator's probe carries no token, and declaring any
     *                       filter chain makes Boot's {@code ManagementWebSecurityAutoConfiguration}
     *                       back off completely, so health closes unless it is permitted again here.
     * @param tenants How the set of accepted realms is decided. Defaults to {@link Tenants#SINGLE_REALM}.
     * @param rules Authorization rules, applied in the order given.
     */
    public Cqrs4jSecurityProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * Describes the effective model in one line, for the start-up log.
     *
     * @return Human readable summary.
     */
    public String describe() {
        final List<String> parts = new ArrayList<>();
        if (permitActuator) {
            parts.add("health and info are open");
        }
        rules.forEach(rule -> parts.add(rule.paths() + " needs any of " + rule.hasAnyRole()));
        parts.add("every other request needs a valid bearer token");
        return String.join(", ", parts);
    }

}
