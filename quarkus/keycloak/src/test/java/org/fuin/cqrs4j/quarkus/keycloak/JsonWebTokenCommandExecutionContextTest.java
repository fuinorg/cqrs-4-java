package org.fuin.cqrs4j.quarkus.keycloak;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for {@link JsonWebTokenCommandExecutionContext}.
 */
class JsonWebTokenCommandExecutionContextTest {

    private static final String ISSUER = "http://localhost:8082/realms/master";

    private JsonWebToken jwt;

    private JsonWebTokenCommandExecutionContext testee;

    @BeforeEach
    void setUp() {
        jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("uuid-123");
        when(jwt.getClaim("preferred_username")).thenReturn("alice");
        when(jwt.getClaim("iss")).thenReturn(ISSUER);
        testee = new JsonWebTokenCommandExecutionContext();
        testee.jwt = jwt;
    }

    @Test
    void testUserAndTenant() {
        assertThat(testee.getUserId()).isEqualTo("uuid-123");
        assertThat(testee.getPreferredUsername()).isEqualTo("alice");
        assertThat(testee.getRealm()).isEqualTo("master");
        assertThat(testee.getTenantId()).isEqualTo(new TenantId("master"));
        assertThat(testee.getUser().getUserId()).isEqualTo("uuid-123");
        assertThat(testee.getUser().getUserName()).isEqualTo("alice");
    }

    @Test
    void testUserRoles() {
        final JsonObject realmAccess = Json.createObjectBuilder()
                .add("roles", Json.createArrayBuilder().add("admin").add("user"))
                .build();
        when(jwt.getClaim("realm_access")).thenReturn(realmAccess);
        assertThat(testee.getUserRoles()).containsExactly(new SimpleRole("admin"), new SimpleRole("user"));
    }

    @Test
    void testUserRolesMissing() {
        when(jwt.getClaim("realm_access")).thenReturn(null);
        assertThat(testee.getUserRoles()).isEmpty();
    }

    @Test
    void testNonStringClaim() {
        when(jwt.getClaim("preferred_username")).thenReturn(Integer.valueOf(42));
        assertThatThrownBy(() -> testee.getPreferredUsername())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preferred_username");
    }

    @Test
    void testIssuerWithoutRealm() {
        when(jwt.getClaim("iss")).thenReturn("noslash");
        assertThatThrownBy(() -> testee.getTenantId()).isInstanceOf(IllegalArgumentException.class);
    }

}
