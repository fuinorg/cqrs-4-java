package org.fuin.cqrs4j.quarkus.keycloak;

import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link JwtTenant}.
 */
class JwtTenantTest {

    @Test
    void testConstruction() {
        final JwtTenant tenant = new JwtTenant("http://localhost:8082/realms/custone");
        assertThat(tenant.getTenantId()).isEqualTo(new TenantId("custone"));
        assertThat(tenant.getIssuer()).isEqualTo("http://localhost:8082/realms/custone");
    }

    @Test
    void testNullIssuer() {
        assertThatThrownBy(() -> new JwtTenant(null)).isInstanceOf(NullPointerException.class);
    }

}
