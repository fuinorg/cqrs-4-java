package org.fuin.cqrs4j.quarkus.keycloak;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test for {@link TenantContextRequestFilter}.
 */
class TenantContextRequestFilterTest {

    private static final String ISSUER = "http://localhost:8082/realms/master";

    private JsonWebToken jwt;

    private WritableTenantContext tenantContext;

    private JwtTenantRepository tenantRepository;

    private TenantContextRequestFilter testee;

    @BeforeEach
    void setUp() {
        jwt = mock(JsonWebToken.class);
        tenantContext = mock(WritableTenantContext.class);
        tenantRepository = mock(JwtTenantRepository.class);
        testee = new TenantContextRequestFilter();
        testee.jwt = jwt;
        testee.tenantContext = tenantContext;
        testee.tenantRepository = tenantRepository;
    }

    @Test
    void testRequestSetsTenant() {
        when(jwt.getIssuer()).thenReturn(ISSUER);
        testee.filter(null);
        verify(tenantRepository).findByIssuer(ISSUER);
        verify(tenantContext).setTenantId(new TenantId("master"));
    }

    @Test
    void testRequestWithoutTokenDoesNothing() {
        when(jwt.getIssuer()).thenReturn(null);
        testee.filter(null);
        verifyNoInteractions(tenantRepository, tenantContext);
    }

    @Test
    void testResponseClearsTenant() {
        testee.filter(null, null);
        verify(tenantContext).clear();
    }

}
