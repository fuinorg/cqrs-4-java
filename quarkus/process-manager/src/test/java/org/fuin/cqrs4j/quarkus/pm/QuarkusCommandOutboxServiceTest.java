package org.fuin.cqrs4j.quarkus.pm;
import org.fuin.cqrs4j.jpa.pm.*;

import jakarta.json.bind.Jsonb;
import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.Command;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.objects4j.jsonb.JsonbProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link QuarkusCommandOutboxService} class (decision logic against a mocked entity manager). The
 * query-based methods (fetchBatch / counts) are exercised against a real database in the reference-app IT.
 */
public class QuarkusCommandOutboxServiceTest {

    private static final String CMD_ID = "f910c6d7-debc-46e1-ae02-9ca6f4658cf5";

    private EntityManager em;

    private JsonbProvider jsonbProvider;

    private QuarkusCommandOutboxService testee;

    @BeforeEach
    public void setUp() {
        em = mock(EntityManager.class);
        jsonbProvider = mock(JsonbProvider.class);
        testee = new QuarkusCommandOutboxService();
        testee.em = em;
        testee.jsonbProvider = jsonbProvider;
    }

    @Test
    public void testEnqueue() {
        // PREPARE
        final EventId eventId = new EventId();
        final Command command = mock(Command.class);
        final Jsonb jsonb = mock(Jsonb.class);
        when(command.getEventId()).thenReturn(eventId);
        when(command.getEventType()).thenReturn(new EventType("ReserveStockCommand"));
        when(jsonbProvider.jsonb()).thenReturn(jsonb);
        when(jsonb.toJson(command)).thenReturn("{\"json\":true}");

        // TEST
        testee.enqueue(command);

        // VERIFY
        final org.mockito.ArgumentCaptor<ProcessManagerCommandOutbox> captor =
                org.mockito.ArgumentCaptor.forClass(ProcessManagerCommandOutbox.class);
        verify(em).persist(captor.capture());
        final ProcessManagerCommandOutbox persisted = captor.getValue();
        assertThat(persisted.getId()).isEqualTo(eventId.asString());
        assertThat(persisted.getType()).isEqualTo("ReserveStockCommand");
        assertThat(persisted.getJson()).isEqualTo("{\"json\":true}");
        assertThat(persisted.getRetries()).isZero();
    }

    @Test
    public void testDeleteRemovesWhenPresent() {
        // PREPARE
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox(CMD_ID, "A", "{}", 1L);
        when(em.find(ProcessManagerCommandOutbox.class, CMD_ID)).thenReturn(outbox);

        // TEST
        testee.delete(CMD_ID);

        // VERIFY
        verify(em).remove(outbox);
    }

    @Test
    public void testRecordFailureIncrementsBelowCap() {
        // PREPARE
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox(CMD_ID, "A", "{}", 1L);
        when(em.find(ProcessManagerCommandOutbox.class, CMD_ID)).thenReturn(outbox);

        // TEST
        testee.recordFailure(CMD_ID, "boom", 5);

        // VERIFY
        assertThat(outbox.getRetries()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("boom");
        verify(em, never()).persist(any());
        verify(em, never()).remove(any());
    }

    @Test
    public void testRecordFailureDeadLettersAtCap() {
        // PREPARE
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox(CMD_ID, "A", "{}", 1L);
        when(em.find(ProcessManagerCommandOutbox.class, CMD_ID)).thenReturn(outbox);

        // TEST: with a cap of 1, the first failure dead-letters
        testee.recordFailure(CMD_ID, "boom", 1);

        // VERIFY
        final org.mockito.ArgumentCaptor<ProcessManagerCommandDeadLetter> captor =
                org.mockito.ArgumentCaptor.forClass(ProcessManagerCommandDeadLetter.class);
        verify(em).persist(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(CMD_ID);
        assertThat(captor.getValue().getError()).isEqualTo("boom");
        verify(em).remove(outbox);
    }

}
