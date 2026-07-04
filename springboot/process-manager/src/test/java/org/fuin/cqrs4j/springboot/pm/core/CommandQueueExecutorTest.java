package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.cqrs4j.springboot.pm.core.CommandOutboxService.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link CommandQueueExecutor} class.
 */
@ExtendWith(MockitoExtension.class)
class CommandQueueExecutorTest {

    @Mock
    private CommandOutboxService outboxService;

    @Mock
    private CommandRestClient commandRestClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CommandQueueExecutor newExecutor(final int maxRetries) {
        final CommandQueueConfig config = new CommandQueueConfig("http://localhost", "*/5 * * * * *", 100, maxRetries);
        return new CommandQueueExecutor(outboxService, commandRestClient, config, transactionManager);
    }

    @Test
    void testSuccessfulDeliveryIsDeleted() {
        // PREPARE
        final CommandQueueExecutor testee = newExecutor(5);
        when(outboxService.fetchBatch(100)).thenReturn(List.of(new Entry("id-1", "A", null, "{1}")));
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
        final CommandQueueExecutor testee = newExecutor(5);
        when(outboxService.fetchBatch(100)).thenReturn(List.of(new Entry("id-1", "A", null, "{1}")));
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
        final CommandQueueExecutor testee = newExecutor(5);
        when(outboxService.fetchBatch(100)).thenReturn(List.of(
                new Entry("id-1", "A", null, "{1}"),
                new Entry("id-2", "B", null, "{2}")));
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
        final CommandQueueExecutor testee = newExecutor(5);
        when(outboxService.fetchBatch(100)).thenReturn(List.of());

        // TEST
        testee.drain();

        // VERIFY
        verify(outboxService, never()).delete(anyString());
        verify(outboxService, never()).recordFailure(anyString(), anyString(), anyInt());
    }

}
