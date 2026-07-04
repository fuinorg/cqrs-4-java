package org.fuin.cqrs4j.quarkus.keycloak;

import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.ThreadLocalTenantContext;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link TenantContextProducer}.
 */
class TenantContextProducerTest {

    @Test
    void testProducesThreadLocalContext() {
        final WritableTenantContext ctx = new TenantContextProducer().writableTenantContext();
        assertThat(ctx).isInstanceOf(ThreadLocalTenantContext.class);
        try {
            ctx.setTenantId(new TenantId("master"));
            assertThat(ctx.getTenantId()).contains(new TenantId("master"));
        } finally {
            ctx.clear();
        }
        assertThat(ctx.getTenantId()).isEmpty();
    }

}
