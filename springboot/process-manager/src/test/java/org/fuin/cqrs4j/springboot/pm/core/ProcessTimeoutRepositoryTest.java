package org.fuin.cqrs4j.springboot.pm.core;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link ProcessTimeoutRepository} class (decision logic against a mocked entity manager). The
 * query-based methods (fetchDue / counts) are exercised against a real database in the Docker-free slice test.
 */
@ExtendWith(MockitoExtension.class)
class ProcessTimeoutRepositoryTest {

    @Mock
    private EntityManager em;

    private ProcessTimeoutRepository testee;

    @BeforeEach
    void setUp() throws Exception {
        testee = new ProcessTimeoutRepository();
        final Field field = ProcessTimeoutRepository.class.getDeclaredField("em");
        field.setAccessible(true);
        field.set(testee, em);
    }

    @Test
    void testArmPersistsWhenAbsent() {
        // PREPARE
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(null);

        // TEST
        testee.arm("p-1", "OrderProcess", 2, 5000L, "await-ack");

        // VERIFY
        final ArgumentCaptor<ProcessManagerTimeout> captor = ArgumentCaptor.forClass(ProcessManagerTimeout.class);
        verify(em).persist(captor.capture());
        final ProcessManagerTimeout persisted = captor.getValue();
        assertThat(persisted.getProcessId()).isEqualTo("p-1");
        assertThat(persisted.getProcessType()).isEqualTo("OrderProcess");
        assertThat(persisted.getProcessVersion()).isEqualTo(2);
        assertThat(persisted.getDeadlineTs()).isEqualTo(5000L);
        assertThat(persisted.getPayload()).isEqualTo("await-ack");
    }

    @Test
    void testArmRearmsWhenPresent() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST
        testee.arm("p-1", "OrderProcess", 3, 9000L, "await-ship");

        // VERIFY: updated in place, no new row
        verify(em, never()).persist(any());
        assertThat(existing.getProcessVersion()).isEqualTo(3);
        assertThat(existing.getDeadlineTs()).isEqualTo(9000L);
        assertThat(existing.getPayload()).isEqualTo("await-ship");
    }

    @Test
    void testCancelRemovesWhenPresent() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST
        testee.cancel("p-1");

        // VERIFY
        verify(em).remove(existing);
    }

    @Test
    void testCancelNoopWhenAbsent() {
        // PREPARE
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(null);

        // TEST
        testee.cancel("p-1");

        // VERIFY
        verify(em, never()).remove(any());
    }

    @Test
    void testRecordFailureIncrementsBelowCap() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST
        testee.recordFailure("p-1", "boom", 5);

        // VERIFY: retry counted, not yet dead-lettered
        assertThat(existing.getRetries()).isEqualTo(1);
        assertThat(existing.getLastError()).isEqualTo("boom");
        verify(em, never()).persist(any());
        verify(em, never()).remove(any());
    }

    @Test
    void testRecordFailureDeadLettersAtCap() {
        // PREPARE
        final ProcessManagerTimeout existing = new ProcessManagerTimeout("p-1", "OrderProcess", 1, 1000L, null, 100L);
        when(em.find(ProcessManagerTimeout.class, "p-1")).thenReturn(existing);

        // TEST: with a cap of 1, the first failure dead-letters
        testee.recordFailure("p-1", "boom", 1);

        // VERIFY: moved to dead-letter and removed
        final ArgumentCaptor<ProcessManagerTimeoutDeadLetter> captor =
                ArgumentCaptor.forClass(ProcessManagerTimeoutDeadLetter.class);
        verify(em).persist(captor.capture());
        assertThat(captor.getValue().getProcessId()).isEqualTo("p-1");
        assertThat(captor.getValue().getError()).isEqualTo("boom");
        verify(em).remove(existing);
    }

}
