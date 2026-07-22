package org.fuin.cqrs4j.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link CommandDeliveryException}.
 */
class CommandDeliveryExceptionTest {

    @Test
    void testCreate() {
        final IllegalStateException cause = new IllegalStateException("boom");

        final CommandDeliveryException testee = new CommandDeliveryException("failed", 400, cause);

        assertThat(testee.getMessage()).isEqualTo("failed");
        assertThat(testee.getStatusCode()).isEqualTo(400);
        assertThat(testee.getCause()).isSameAs(cause);
    }

    @Test
    void testIsNotTransient() {
        // A rejected command must never be classified as a transient failure - retrying it can only burn
        // the retry budget until the command is dead-lettered.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new CommandDeliveryException("rejected", 400, null))).isFalse();
    }

    @Test
    void testPermanentWinsOverAnIoCause() {
        // Even with an IOException somewhere in the chain, an answered 4xx stays permanent.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new CommandDeliveryException("rejected", 422, new java.io.IOException("noise")))).isFalse();
    }

}
