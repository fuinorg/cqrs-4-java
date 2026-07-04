package org.fuin.cqrs4j.quarkus.pm;
import org.fuin.cqrs4j.jpa.pm.*;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.Command;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.EnhancedMimeType;
import org.fuin.esc.api.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link CommandOutboxService} class (decision logic against a mocked entity manager). The
 * query-based methods (fetchBatch / counts) are exercised against a real database in the reference-app IT.
 */
public class CommandOutboxServiceTest {

    private static final String CMD_ID = "f910c6d7-debc-46e1-ae02-9ca6f4658cf5";

    private EntityManager em;

    private Serializer commandSerializer;

    private CommandOutboxService testee;

    @BeforeEach
    public void setUp() {
        em = mock(EntityManager.class);
        commandSerializer = mock(Serializer.class);
        testee = new CommandOutboxService();
        testee.em = em;
        testee.commandSerializer = commandSerializer;
    }

    @Test
    public void testEnqueue() {
        // PREPARE
        final EventId eventId = new EventId();
        final Command command = mock(Command.class);
        when(command.getEventId()).thenReturn(eventId);
        when(command.getEventType()).thenReturn(new EventType("ReserveStockCommand"));
        when(commandSerializer.getMimeType()).thenReturn(EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8));
        when(commandSerializer.marshal(eq(command), any())).thenReturn("{\"json\":true}".getBytes(StandardCharsets.UTF_8));

        // TEST
        testee.enqueue(command);

        // VERIFY
        final org.mockito.ArgumentCaptor<ProcessManagerCommandOutbox> captor =
                org.mockito.ArgumentCaptor.forClass(ProcessManagerCommandOutbox.class);
        verify(em).persist(captor.capture());
        final ProcessManagerCommandOutbox persisted = captor.getValue();
        assertThat(persisted.getId()).isEqualTo(eventId.asString());
        assertThat(persisted.getType()).isEqualTo("ReserveStockCommand");
        assertThat(persisted.getContentType()).contains("application/json");
        assertThat(persisted.getJson()).isEqualTo("{\"json\":true}");
        assertThat(persisted.getRetries()).isZero();
    }

    @Test
    public void testDeleteRemovesWhenPresent() {
        // PREPARE
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox(CMD_ID, "A", "application/json", "{}", 1L);
        when(em.find(ProcessManagerCommandOutbox.class, CMD_ID)).thenReturn(outbox);

        // TEST
        testee.delete(CMD_ID);

        // VERIFY
        verify(em).remove(outbox);
    }

    @Test
    public void testRecordFailureIncrementsBelowCap() {
        // PREPARE
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox(CMD_ID, "A", "application/json", "{}", 1L);
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
        final ProcessManagerCommandOutbox outbox = new ProcessManagerCommandOutbox(CMD_ID, "A", "application/json", "{}", 1L);
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
