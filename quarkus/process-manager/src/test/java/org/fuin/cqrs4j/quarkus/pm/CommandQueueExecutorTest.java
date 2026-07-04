package org.fuin.cqrs4j.quarkus.pm;

import org.fuin.cqrs4j.quarkus.pm.CommandOutboxService.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link CommandQueueExecutor} class.
 */
class CommandQueueExecutorTest {

    private CommandOutboxService outboxService;

    private CommandRestClient commandRestClient;

    private CommandQueueConfig config;

    private CommandQueueExecutor testee;

    @BeforeEach
    void setUp() {
        outboxService = mock(CommandOutboxService.class);
        commandRestClient = mock(CommandRestClient.class);
        config = mock(CommandQueueConfig.class);
        when(config.getBatchSize()).thenReturn(100);
        when(config.getMaxRetries()).thenReturn(5);
        testee = new CommandQueueExecutor();
        testee.outboxService = outboxService;
        testee.commandRestClient = commandRestClient;
        testee.config = config;
    }

    @Test
    void testSuccessfulDeliveryIsDeleted() {
        // PREPARE
        when(outboxService.fetchBatch(100)).thenReturn(List.of(new Entry("id-1", "A", "application/json", "{1}")));
        when(commandRestClient.cmd("A", "application/json", "{1}")).thenReturn("OK");

        // TEST
        testee.drain();

        // VERIFY
        verify(commandRestClient).cmd("A", "application/json", "{1}");
        verify(outboxService).delete("id-1");
        verify(outboxService, never()).recordFailure(anyString(), anyString(), anyInt());
    }

    @Test
    void testFailedDeliveryIsRecorded() {
        // PREPARE
        when(outboxService.fetchBatch(100)).thenReturn(List.of(new Entry("id-1", "A", "application/json", "{1}")));
        when(commandRestClient.cmd("A", "application/json", "{1}")).thenThrow(new RuntimeException("boom"));

        // TEST
        testee.drain();

        // VERIFY
        verify(outboxService).recordFailure("id-1", "boom", 5);
        verify(outboxService, never()).delete(anyString());
    }

    @Test
    void testOneFailureDoesNotStopOthers() {
        // PREPARE
        when(outboxService.fetchBatch(100)).thenReturn(List.of(
                new Entry("id-1", "A", "application/json", "{1}"),
                new Entry("id-2", "B", "application/json", "{2}")));
        when(commandRestClient.cmd("A", "application/json", "{1}")).thenThrow(new RuntimeException("boom"));
        when(commandRestClient.cmd("B", "application/json", "{2}")).thenReturn("OK");

        // TEST
        testee.drain();

        // VERIFY
        verify(outboxService).recordFailure(eq("id-1"), anyString(), eq(5));
        verify(outboxService).delete("id-2");
    }

    @Test
    void testEmptyQueueDoesNothing() {
        // PREPARE
        when(outboxService.fetchBatch(100)).thenReturn(List.of());

        // TEST
        testee.drain();

        // VERIFY
        verify(outboxService, never()).delete(anyString());
        verify(outboxService, never()).recordFailure(anyString(), anyString(), anyInt());
    }

}
