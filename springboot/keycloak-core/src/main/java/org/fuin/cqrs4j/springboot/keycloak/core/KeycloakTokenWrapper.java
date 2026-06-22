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

import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.User;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;

/**
 * Helper class to extract values from the {@link JwtAuthenticationToken}.
 */
@ThreadSafe
public class KeycloakTokenWrapper implements CommandExecutionContext {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtAuthenticationToken auth;

    /**
     * Constructor with authentication information.
     *
     * @param auth Expected to be a {@link JwtAuthenticationToken} for an authenticated user.
     */
    public KeycloakTokenWrapper(Authentication auth) {
        this.auth = token(auth);
    }

    /**
     * Returns the user ID.
     *
     * @return UUID of the user.
     */
    public String getUserId() {
        return auth.getName();
    }

    /**
     * Returns the preferred username.
     *
     * @return Username.
     */
    public String getPreferredUsername() {
        return stringAttribute("preferred_username");
    }

    /**
     * Returns the realm.
     *
     * @return Realm name.
     */
    public String getRealm() {
        final String iss = getIss();
        final int p = iss.lastIndexOf('/');
        if (p < 0) {
            throw new IllegalArgumentException("Failed to extract realm from 'iss': " + iss);
        }
        return iss.substring(p + 1);
    }

    /**
     * Returns the issuer.
     *
     * @return Issuer.
     */
    public String getIss() {
        return stringAttribute("iss");
    }

    /**
     * Returns the user roles as simple roles.
     *
     * @return User's roles.
     */
    public List<SimpleRole> getUserRoles() {
        final Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .map(SimpleRole::new)
                .toList();
    }

    private String stringAttribute(String key) {
        final Object value = auth.getTokenAttributes().get(key);
        if (value instanceof String v) {
            return v;
        }
        throw new IllegalArgumentException("Expected key '" + key
                + "' to be of type 'String', but was: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static JwtAuthenticationToken token(Authentication auth) {
        if (!auth.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated!");
        }
        if (auth instanceof JwtAuthenticationToken token) {
            return token;
        }
        throw new IllegalStateException("Expected 'auth' to be of type '"
                + JwtAuthenticationToken.class.getSimpleName() + "' but was: "
                + auth.getClass().getName());
    }

    @Override
    public TenantId getTenantId() {
        return new TenantId(getRealm());
    }

    @Override
    public User getUser() {
        return new User() {
            @Override
            public String getUserId() {
                return KeycloakTokenWrapper.this.getUserId();
            }

            @Override
            public String getUserName() {
                return getPreferredUsername();
            }
        };
    }
}
