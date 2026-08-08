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

import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * Converts JWT token into authentication token.
 * <p>
 * Only <b>realm</b> roles are mapped ({@code realm_access.roles}), each to a {@code ROLE_}-prefixed
 * authority, alongside the standard {@code SCOPE_} authorities. Client roles
 * ({@code resource_access.*.roles}) are deliberately not considered.
 * <p>
 * A token carrying no realm roles at all is valid - Keycloak omits the {@code realm_access} claim for a
 * user without any realm role - and yields no {@code ROLE_} authority. A claim that is present but has
 * the wrong shape is a malformed token and is rejected with an {@link InvalidBearerTokenException}, so
 * the caller sees a 401 instead of a 500.
 */
@ThreadSafe
public final class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";

    private static final String REALM_ACCESS = "realm_access";

    private static final String ROLES = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        final Collection<GrantedAuthority> standardRoles = new JwtGrantedAuthoritiesConverter().convert(source);
        final Collection<? extends GrantedAuthority> customRoles = roles(source);
        return new JwtAuthenticationToken(source,
                Stream.concat(standardRoles.stream(), customRoles.stream())
                        .collect(toSet()));
    }

    private Collection<? extends GrantedAuthority> roles(Jwt jwt) {

        final Object realmAccess = jwt.getClaim(REALM_ACCESS);
        if (realmAccess == null) {
            // Keycloak omits the claim for a user without any realm role.
            return Collections.emptySet();
        }
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            throw malformed("Expected Map, but claim '" + REALM_ACCESS + "' was: "
                    + realmAccess.getClass().getName());
        }

        final Object rolesClaim = realmAccessMap.get(ROLES);
        if (rolesClaim == null) {
            return Collections.emptySet();
        }
        if (!(rolesClaim instanceof List<?> roles)) {
            throw malformed("Expected List, but claim '" + REALM_ACCESS + "." + ROLES + "' was: "
                    + rolesClaim.getClass().getName());
        }

        final Set<GrantedAuthority> authorities = new HashSet<>();
        for (final Object role : roles) {
            if (!(role instanceof String name)) {
                throw malformed("Expected String, but an entry of claim '" + REALM_ACCESS + "." + ROLES
                        + "' was: " + (role == null ? "null" : role.getClass().getName()));
            }
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + name));
        }
        return authorities;

    }

    private static InvalidBearerTokenException malformed(final String message) {
        return new InvalidBearerTokenException(message);
    }

}
