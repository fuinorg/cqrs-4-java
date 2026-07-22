package org.fuin.cqrs4j.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link TransientCommandDeliveryException}.
 */
class TransientCommandDeliveryExceptionTest {

    @Test
    void testCreate() {
        final TransientCommandDeliveryException testee =
                new TransientCommandDeliveryException("unreachable", 503, null);

        assertThat(testee.getMessage()).isEqualTo("unreachable");
        assertThat(testee.getStatusCode()).isEqualTo(503);
        assertThat(testee).isInstanceOf(CommandDeliveryException.class);
    }

    @Test
    void testIsTransient() {
        // Drives the circuit breaker: only this variant may open it.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new TransientCommandDeliveryException("unreachable", 503, null))).isTrue();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new TransientCommandDeliveryException("timeout", 0, new java.net.SocketTimeoutException()))).isTrue();
    }

}
