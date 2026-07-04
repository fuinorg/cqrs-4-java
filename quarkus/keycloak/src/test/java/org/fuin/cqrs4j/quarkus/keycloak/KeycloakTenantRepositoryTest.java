package org.fuin.cqrs4j.quarkus.keycloak;

import jakarta.enterprise.event.Event;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link KeycloakTenantRepository}.
 */
class KeycloakTenantRepositoryTest {

    private static final String MASTER = "http://localhost:8082/realms/master";

    private static final String CUST_ONE = "http://localhost:8082/realms/custone";

    private Event<TenantAddedEvent> event;

    private KeycloakTenantRepository testee;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        event = mock(Event.class);
        testee = new KeycloakTenantRepository(MASTER, event);
    }

    @Test
    void testFindByIssuerDiscoversAndFiresEvent() {
        assertThat(testee.getTenantIds()).isEmpty();

        assertThat(testee.findByIssuer(CUST_ONE)).isPresent()
                .get().extracting(JwtTenant::getTenantId).isEqualTo(new TenantId("custone"));

        final ArgumentCaptor<TenantAddedEvent> captor = ArgumentCaptor.forClass(TenantAddedEvent.class);
        verify(event).fire(captor.capture());
        assertThat(captor.getValue().tenant().getTenantId()).isEqualTo(new TenantId("custone"));

        assertThat(testee.getTenantIds()).containsExactly(new TenantId("custone"));
    }

    @Test
    void testFindByIssuerCaches() {
        testee.findByIssuer(CUST_ONE);
        testee.findByIssuer(CUST_ONE);
        // Event fired only once for the same (already cached) tenant.
        verify(event, times(1)).fire(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testFindByIssuerRejectsForeignIssuer() {
        assertThatThrownBy(() -> testee.findByIssuer("http://evil.example.com/realms/custone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not start with");
    }

}
