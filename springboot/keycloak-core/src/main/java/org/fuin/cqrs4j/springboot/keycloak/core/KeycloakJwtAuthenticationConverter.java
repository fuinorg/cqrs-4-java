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

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * Converts JWT token into authentication token.
 */
public final class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        final Collection<GrantedAuthority> standardRoles = new JwtGrantedAuthoritiesConverter().convert(source);
        final Collection<? extends GrantedAuthority> customRoles = roles(source);
        return new JwtAuthenticationToken(source,
                Stream.concat(standardRoles.stream(), customRoles.stream())
                        .collect(toSet()));
    }

    private Collection<? extends GrantedAuthority> roles(Jwt jwt) {
        if (jwt.getClaim("realm_access") instanceof Map realmAccess) {
            final Object rolesClaim = realmAccess.get("roles");
            if (rolesClaim instanceof List roles) {
                return ((List<String>) roles).stream()
                        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                        .collect(toSet());
            } else {
                throw new IllegalArgumentException("Expected List, but claim 'realm_access.roles' was: "
                        + (rolesClaim == null ? "null" : rolesClaim.getClass().getName()));
            }
        } else {
            throw new IllegalArgumentException("Expected Map, but claim 'realm_access' was: " + jwt.getClaim("realm_access").getClass().getName());
        }
    }

}
