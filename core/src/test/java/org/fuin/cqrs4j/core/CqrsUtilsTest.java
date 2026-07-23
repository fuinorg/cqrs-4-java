package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EventType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CqrsUtils} class.
 */
class CqrsUtilsTest {

    @Test
    void testCalculateAdler32Checksum() {

        assertThat(CqrsUtils.calculateAdler32Checksum(List.of(new EventType("A"))))
                .isEqualTo(4325442L);

        assertThat(CqrsUtils.calculateAdler32Checksum(List.of(new EventType("A"), new EventType("B"))))
                .isEqualTo(12976260L);

        assertThat(CqrsUtils.calculateAdler32Checksum(List.of(new EventType("CustomerCreatedEvent"), new EventType("CustomerDeletedEvent"))))
                .isEqualTo(1230901272L);

        assertThat(CqrsUtils.calculateAdler32Checksum(List.of(new EventType("CustomerDeletedEvent"), new EventType("CustomerCreatedEvent"))))
                .isEqualTo(1230901272L);

    }


    @Test
    void testIsTransientInfrastructureFailureForCallTimeout() {
        // A remote call that ran into its timeout: esc reports it as an unchecked exception with a
        // TimeoutException as cause. Without this a polling caller logs an error on every run.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new TimeoutException())).isTrue();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new RuntimeException("The event store call 'getStatus' did not complete within 30000 ms",
                        new TimeoutException()))).isTrue();
    }

    @Test
    void testIsTransientInfrastructureFailureForConnectivity() {
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new IOException())).isTrue();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new ConnectException())).isTrue();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new SQLException())).isTrue();
    }

    @Test
    void testIsTransientInfrastructureFailureWalksCauseChain() {
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new IllegalStateException("outer", new RuntimeException("middle", new ConnectException())))).isTrue();
    }

    @Test
    void testIsTransientInfrastructureFailureForUnexpectedError() {
        // A programming / configuration error (like the missing CDI request context that once hid behind
        // a "could not reach the event store" debug line) must NOT be classified as transient.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new IllegalStateException("boom"))).isFalse();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new NullPointerException())).isFalse();
    }

    @Test
    void testIsTransientInfrastructureFailureForDatabaseHiccup() {
        // A lock that could not be obtained in time, or a query that timed out, means the database is
        // struggling - the next tick may well succeed.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new jakarta.persistence.LockTimeoutException()))
                .isTrue();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new jakarta.persistence.QueryTimeoutException()))
                .isTrue();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new jakarta.persistence.PessimisticLockException()))
                .isTrue();
    }

    @Test
    void testAnswerAboutTheDataIsNotTransient() {
        // These live in the same packages as the failures above, but the database answered and the answer is
        // about the data. Retrying cannot change it, and treating it as transient would hide it at DEBUG and
        // open the projection circuit breaker for every other view.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new jakarta.persistence.OptimisticLockException()))
                .isFalse();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new jakarta.persistence.EntityExistsException()))
                .isFalse();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(new jakarta.persistence.NoResultException()))
                .isFalse();
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new java.sql.SQLIntegrityConstraintViolationException())).isFalse();
    }

    @Test
    void testAnswerAboutTheDataWinsOverAWrappingPersistenceException() {
        // JPA wraps the conflict in a RollbackException on commit - the outermost type must not decide.
        assertThat(CqrsUtils.isTransientInfrastructureFailure(
                new jakarta.persistence.RollbackException(new jakarta.persistence.OptimisticLockException())))
                .isFalse();
    }

    @Test
    void testIsTransientInfrastructureFailureForNull() {
        assertThat(CqrsUtils.isTransientInfrastructureFailure(null)).isFalse();
    }

}
