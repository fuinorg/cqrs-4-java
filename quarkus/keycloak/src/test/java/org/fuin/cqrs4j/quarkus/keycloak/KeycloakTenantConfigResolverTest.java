package org.fuin.cqrs4j.quarkus.keycloak;

import io.quarkus.oidc.OidcTenantConfig;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link KeycloakTenantConfigResolver}.
 */
class KeycloakTenantConfigResolverTest {

    private static final String MASTER = "http://localhost:8082/realms/master";

    private JwtTenantRepository tenantRepository;

    private KeycloakTenantConfigResolver testee;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(JwtTenantRepository.class);
        testee = new KeycloakTenantConfigResolver();
        testee.tenantRepository = tenantRepository;
    }

    private static String bearer(final String issuer) {
        return "Bearer " + KeycloakRealmsTest.jwt("{\"iss\":\"" + issuer + "\",\"sub\":\"1\"}");
    }

    @Test
    void testNoAuthorizationHeader() {
        assertThat(testee.resolveConfig(null)).isNull();
    }

    @Test
    void testNonBearerHeader() {
        assertThat(testee.resolveConfig("Basic dXNlcjpwYXNz")).isNull();
    }

    @Test
    void testBearerTokenResolvesTenant() {
        final OidcTenantConfig config = testee.resolveConfig(bearer(MASTER));
        assertThat(config).isNotNull();
        assertThat(config.tenantId()).contains("master");
        verify(tenantRepository).findByIssuer(MASTER);
    }

    @Test
    void testUnknownIssuerFallsBackToDefault() {
        final String foreign = "http://evil.example.com/realms/master";
        when(tenantRepository.findByIssuer(foreign)).thenThrow(new IllegalArgumentException("bad"));
        assertThat(testee.resolveConfig(bearer(foreign))).isNull();
    }

    @Test
    void testResolveReadsAuthorizationHeader() {
        final RoutingContext routingContext = mock(RoutingContext.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(routingContext.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn(bearer(MASTER));

        final OidcTenantConfig config = testee.resolve(routingContext, null).await().indefinitely();
        assertThat(config).isNotNull();
        assertThat(config.tenantId()).contains("master");
    }

}
