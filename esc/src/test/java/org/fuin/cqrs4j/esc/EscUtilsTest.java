package org.fuin.cqrs4j.esc;

import org.fuin.esc.api.EscConnectionException;
import org.fuin.esc.api.StreamNotFoundException;
import org.fuin.esc.api.SimpleStreamId;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link EscUtils} class.
 */
class EscUtilsTest {

    @Test
    void testEscConnectionExceptionIsTransient() {
        // The whole point of the typed exception: one instanceof settles it.
        assertThat(EscUtils.isTransientInfrastructureFailure(
                new EscConnectionException("Store not reachable"))).isTrue();
    }

    @Test
    void testWrappedEscConnectionExceptionIsTransient() {
        assertThat(EscUtils.isTransientInfrastructureFailure(
                new IllegalStateException("outer", new EscConnectionException("Store not reachable")))).isTrue();
    }

    @Test
    void testFallsBackToTheNeutralClassification() {
        // Failures raised below the event store abstraction still have to be recognised.
        assertThat(EscUtils.isTransientInfrastructureFailure(new ConnectException())).isTrue();
        assertThat(EscUtils.isTransientInfrastructureFailure(new TimeoutException())).isTrue();
    }

    @Test
    void testBusinessAndProgrammingErrorsAreNotTransient() {
        // A business answer from the store must never be retried as if it were a connectivity problem.
        assertThat(EscUtils.isTransientInfrastructureFailure(
                new StreamNotFoundException(new SimpleStreamId("MyStream")))).isFalse();
        assertThat(EscUtils.isTransientInfrastructureFailure(new IllegalStateException("boom"))).isFalse();
        assertThat(EscUtils.isTransientInfrastructureFailure(null)).isFalse();
    }

}
