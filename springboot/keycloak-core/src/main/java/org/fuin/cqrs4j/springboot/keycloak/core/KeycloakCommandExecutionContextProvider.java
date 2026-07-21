package org.fuin.cqrs4j.springboot.keycloak.core;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.springboot.command.core.CommandExecutionContextProvider;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * Derives the execution context from the Keycloak token of the current request: the realm becomes
 * the tenant and the token's roles become the user roles.
 * <p>
 * This replaces the default provider, which only knows the authenticated name. It is also what makes
 * the generic command endpoint usable with Keycloak without the application writing a controller
 * that takes an {@code Authentication} parameter.
 */
@ThreadSafe
public class KeycloakCommandExecutionContextProvider implements CommandExecutionContextProvider {

    @Override
    public CommandExecutionContext current() {
        return new KeycloakTokenWrapper(authentication());
    }

    @Override
    public List<SimpleRole> currentUserRoles() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Collections.emptyList();
        }
        return new KeycloakTokenWrapper(auth).getUserRoles();
    }

    private Authentication authentication() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException(
                    "No authentication available - the command endpoint requires an authenticated caller");
        }
        return auth;
    }

}
