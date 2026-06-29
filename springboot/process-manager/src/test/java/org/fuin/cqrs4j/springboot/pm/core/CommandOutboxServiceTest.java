package org.fuin.cqrs4j.springboot.pm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.fuin.cqrs4j.springboot.pm.core.CommandOutboxService.Entry;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.cqrs4j.core.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link CommandOutboxService} class.
 */
@ExtendWith(MockitoExtension.class)
class CommandOutboxServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EntityManager em;

    private CommandOutboxService testee;

    @BeforeEach
    void setUp() throws Exception {
        testee = new CommandOutboxService(objectMapper);
        final Field field = CommandOutboxService.class.getDeclaredField("em");
        field.setAccessible(true);
        field.set(testee, em);
    }

    @Test
    void testEnqueue() throws Exception {
        // PREPARE
        final EventId eventId = new EventId();
        final Command command = org.mockito.Mockito.mock(Command.class);
        when(command.getEventId()).thenReturn(eventId);
        when(command.getEventType()).thenReturn(new EventType("ReserveStockCommand"));
        when(objectMapper.writeValueAsString(command)).thenReturn("{\"json\":true}");

        // TEST
        testee.enqueue(command);

        // VERIFY
        final ArgumentCaptor<ProcessManagerCommandOutbox> captor = ArgumentCaptor.forClass(ProcessManagerCommandOutbox.class);
        verify(em).persist(captor.capture());
        final ProcessManagerCommandOutbox persisted = captor.getValue();
        assertThat(persisted.getId()).isEqualTo(eventId.asString());
        assertThat(persisted.getType()).isEqualTo("ReserveStockCommand");
        assertThat(persisted.getJson()).isEqualTo("{\"json\":true}");
        assertThat(persisted.getRetries()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFetchBatch() {
        // PREPARE
        final TypedQuery<ProcessManagerCommandOutbox> query = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(ProcessManagerCommandOutbox.class))).thenReturn(query);
        when(query.setMaxResults(50)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(
                new ProcessManagerCommandOutbox("id-1", "A", "{1}", 1L),
                new ProcessManagerCommandOutbox("id-2", "B", "{2}", 2L)));

        // TEST
        final List<Entry> result = testee.fetchBatch(50);

        // VERIFY
        assertThat(result).containsExactly(new Entry("id-1", "A", "{1}"), new Entry("id-2", "B", "{2}"));
    }

    @Test
    void testDeleteExisting() {
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-1", "A", "{}", 1L);
        when(em.find(ProcessManagerCommandOutbox.class, "id-1")).thenReturn(outbox);

        testee.delete("id-1");

        verify(em).remove(outbox);
    }

    @Test
    void testDeleteMissing() {
        when(em.find(ProcessManagerCommandOutbox.class, "id-x")).thenReturn(null);

        testee.delete("id-x");

        verify(em, never()).remove(any());
    }

    @Test
    void testRecordFailureRetry() {
        // PREPARE - retries 0, below max (3)
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-1", "A", "{}", 1L);
        when(em.find(ProcessManagerCommandOutbox.class, "id-1")).thenReturn(outbox);

        // TEST
        testee.recordFailure("id-1", "boom", 3);

        // VERIFY - just incremented, not dead-lettered
        assertThat(outbox.getRetries()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("boom");
        verify(em, never()).persist(any());
        verify(em, never()).remove(any());
    }

    @Test
    void testRecordFailureDeadLetter() {
        // PREPARE - already failed twice, max is 3 -> next failure dead-letters
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox("id-1", "A", "{}", 1L);
        outbox.recordFailure("e1");
        outbox.recordFailure("e2");
        when(em.find(ProcessManagerCommandOutbox.class, "id-1")).thenReturn(outbox);

        // TEST
        testee.recordFailure("id-1", "final", 3);

        // VERIFY
        final ArgumentCaptor<ProcessManagerCommandDeadLetter> captor = ArgumentCaptor.forClass(ProcessManagerCommandDeadLetter.class);
        verify(em).persist(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("id-1");
        assertThat(captor.getValue().getRetries()).isEqualTo(3);
        assertThat(captor.getValue().getError()).isEqualTo("final");
        verify(em).remove(outbox);
    }

}
