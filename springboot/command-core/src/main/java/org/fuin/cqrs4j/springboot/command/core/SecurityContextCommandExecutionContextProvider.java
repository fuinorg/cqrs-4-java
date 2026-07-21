package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.User;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * Default provider that derives the execution context from Spring Security's
 * {@link SecurityContextHolder}. It covers the plain case: the authenticated name becomes the user
 * id and the granted authorities become the roles.
 * <p>
 * An unauthenticated request (no security configured, or an anonymous caller) yields the configured
 * fallback user, so an application without security still works. Anything richer - a Keycloak token
 * with a realm as tenant, for example - replaces this bean with its own implementation.
 */
@ThreadSafe
public class SecurityContextCommandExecutionContextProvider implements CommandExecutionContextProvider {

    private final TenantId tenantId;

    private final String anonymousUserId;

    /**
     * Constructor with all data.
     *
     * @param tenantId Tenant reported for every request - this provider knows no multi tenancy.
     * @param anonymousUserId User id reported when the request is not authenticated.
     */
    public SecurityContextCommandExecutionContextProvider(final TenantId tenantId, final String anonymousUserId) {
        this.tenantId = tenantId;
        this.anonymousUserId = anonymousUserId;
    }

    @Override
    public CommandExecutionContext current() {
        final String userId = userId();
        return new CommandExecutionContext() {
            @Override
            public TenantId getTenantId() {
                return tenantId;
            }

            @Override
            public User getUser() {
                return new User() {
                    @Override
                    public String getUserId() {
                        return userId;
                    }

                    @Override
                    public String getUserName() {
                        return userId;
                    }
                };
            }
        };
    }

    @Override
    public List<SimpleRole> currentUserRoles() {
        final Authentication auth = authentication();
        if (auth == null) {
            return Collections.emptyList();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).map(SimpleRole::new).toList();
    }

    private String userId() {
        final Authentication auth = authentication();
        if (auth == null || auth.getName() == null) {
            return anonymousUserId;
        }
        return auth.getName();
    }

    @Nullable
    private Authentication authentication() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth;
    }

}
