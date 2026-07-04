package org.fuin.cqrs4j.quarkus.pm;
import org.fuin.cqrs4j.jpa.pm.*;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link QuarkusProcessTimeoutRepository} class (decision logic against a mocked entity manager).
 */
public class QuarkusProcessTimeoutRepositoryTest {

    private EntityManager em;

    private QuarkusProcessTimeoutRepository testee;

    @BeforeEach
    public void setUp() {
        em = mock(EntityManager.class);
        testee = new QuarkusProcessTimeoutRepository();
        testee.em = em;
    }

    @Test
    public void testArmPersistsWhenAbsent() {
        // PREPARE
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(null);

        // TEST
        testee.arm("p-1", "OrderProcess", 2, 5000L, "await-ack");

        // VERIFY
        final org.mockito.ArgumentCaptor<ProcessManagerTimeout> captor =
                org.mockito.ArgumentCaptor.forClass(ProcessManagerTimeout.class);
        verify(em).persist(captor.capture());
        assertThat(captor.getValue().getProcessId()).isEqualTo("p-1");
        assertThat(captor.getValue().getProcessVersion()).isEqualTo(2);
        assertThat(captor.getValue().getDeadlineTs()).isEqualTo(5000L);
        assertThat(captor.getValue().getPayload()).isEqualTo("await-ack");
    }

    @Test
    public void testArmRearmsWhenPresent() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST
        testee.arm("p-1", "OrderProcess", 3, 9000L, "await-ship");

        // VERIFY: updated in place, no new row
        verify(em, never()).persist(any());
        assertThat(existing.getProcessVersion()).isEqualTo(3);
        assertThat(existing.getDeadlineTs()).isEqualTo(9000L);
    }

    @Test
    public void testCancelRemovesWhenPresent() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST
        testee.cancel("p-1");

        // VERIFY
        verify(em).remove(existing);
    }

    @Test
    public void testRecordFailureIncrementsBelowCap() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST
        testee.recordFailure("p-1", "boom", 5);

        // VERIFY
        assertThat(existing.getRetries()).isEqualTo(1);
        assertThat(existing.getLastError()).isEqualTo("boom");
        verify(em, never()).persist(any());
        verify(em, never()).remove(any());
    }

    @Test
    public void testRecordFailureDeadLettersAtCap() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST: with a cap of 1, the first failure dead-letters
        testee.recordFailure("p-1", "boom", 1);

        // VERIFY
        final org.mockito.ArgumentCaptor<ProcessManagerTimeoutDeadLetter> captor =
                org.mockito.ArgumentCaptor.forClass(ProcessManagerTimeoutDeadLetter.class);
        verify(em).persist(captor.capture());
        assertThat(captor.getValue().getProcessId()).isEqualTo("p-1");
        verify(em).remove(existing);
    }

}
