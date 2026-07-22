package org.fuin.cqrs4j.quarkus.pm;

import io.smallrye.faulttolerance.api.Guard;
import org.fuin.cqrs4j.quarkus.pm.CommandOutboxService.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.concurrent.Callable;

import static org.mockito.ArgumentMatchers.any;
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
        testee.deliveryGuard = passThroughGuard();
    }

    /**
     * A real {@link Guard} needs the SmallRye Fault Tolerance runtime SPI, which is not available outside
     * the container. This one simply runs the call, so the outbox behaviour can be tested on its own.
     *
     * @return Guard that executes without any fault tolerance.
     */
    private static Guard passThroughGuard() {
        final Guard guard = mock(Guard.class);
        try {
            when(guard.call(any(), any(Class.class))).thenAnswer(invocation -> {
                final Callable<?> callable = invocation.getArgument(0);
                return callable.call();
            });
        } catch (final Exception ex) {
            throw new IllegalStateException("Could not create the pass-through guard", ex);
        }
        return guard;
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


    @Test
    void testOpenBreakerDefersWithoutConsumingRetries() throws Exception {
        // PREPARE: the endpoint is known to be down, so the guard rejects without calling it.
        final Guard openGuard = mock(Guard.class);
        when(openGuard.call(any(), any(Class.class)))
                .thenThrow(new org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException("open"));
        testee.deliveryGuard = openGuard;
        when(outboxService.fetchBatch(100)).thenReturn(List.of(
                new Entry("id-1", "A", "application/json", "{1}"),
                new Entry("id-2", "B", "application/json", "{2}")));

        // TEST
        testee.drain();

        // VERIFY: nothing was sent, nothing was deleted, and - the point of the exercise - no failure was
        // recorded. A short outage must not burn the retry budget and dead-letter valid commands.
        verify(commandRestClient, never()).cmd(anyString(), anyString(), anyString());
        verify(outboxService, never()).delete(anyString());
        verify(outboxService, never()).recordFailure(anyString(), anyString(), anyInt());
    }

    @Test
    void testRejectedCommandStillConsumesRetryBudget() throws Exception {
        // PREPARE: the endpoint answered that the command itself is wrong - retrying cannot help, so the
        // failure must be recorded and eventually dead-lettered.
        when(outboxService.fetchBatch(100)).thenReturn(List.of(new Entry("id-1", "A", "application/json", "{1}")));
        when(commandRestClient.cmd("A", "application/json", "{1}"))
                .thenThrow(new org.fuin.cqrs4j.core.CommandDeliveryException("rejected", 400, null));

        // TEST
        testee.drain();

        // VERIFY
        verify(outboxService).recordFailure(eq("id-1"), anyString(), eq(5));
        verify(outboxService, never()).delete(anyString());
    }

}
