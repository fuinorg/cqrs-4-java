package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecurityContextCommandExecutionContextProvider}.
 */
class SecurityContextCommandExecutionContextProviderTest {

    private static final TenantId TENANT = new TenantId("tenant");

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testAuthenticatedCallerBecomesUserAndRoles() {

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("peter", "pw",
                        List.of(new SimpleGrantedAuthority("admin"))));

        final SecurityContextCommandExecutionContextProvider testee =
                new SecurityContextCommandExecutionContextProvider(TENANT, "anonymous");

        assertThat(testee.current().getUser().getUserId()).isEqualTo("peter");
        assertThat(testee.current().getTenantId()).isEqualTo(TENANT);
        assertThat(testee.currentUserRoles()).containsExactly(new SimpleRole("admin"));
    }

    @Test
    void testUnauthenticatedCallerFallsBackToAnonymous() {

        // No authentication in the context: an application without security still has to work.
        final SecurityContextCommandExecutionContextProvider testee =
                new SecurityContextCommandExecutionContextProvider(TENANT, "anonymous");

        assertThat(testee.current().getUser().getUserId()).isEqualTo("anonymous");
        assertThat(testee.currentUserRoles()).isEmpty();
    }

}
