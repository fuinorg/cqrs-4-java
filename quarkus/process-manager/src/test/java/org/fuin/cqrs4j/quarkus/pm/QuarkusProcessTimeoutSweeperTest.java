package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.inject.Instance;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler.DueProcessTimeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link QuarkusProcessTimeoutSweeper} class (the transaction boundary is overridden to run inline).
 */
@SuppressWarnings("unchecked")
public class QuarkusProcessTimeoutSweeperTest {

    private QuarkusProcessTimeoutRepository repository;

    private ProcessTimeoutConfig config;

    private Instance<ProcessTimeoutHandler> handlers;

    private ProcessTimeoutHandler handler;

    private QuarkusProcessTimeoutSweeper testee;

    @BeforeEach
    public void setUp() {
        repository = mock(QuarkusProcessTimeoutRepository.class);
        config = mock(ProcessTimeoutConfig.class);
        handlers = mock(Instance.class);
        handler = mock(ProcessTimeoutHandler.class);
        when(config.getBatchSize()).thenReturn(100);
        when(config.getMaxRetries()).thenReturn(5);
        testee = new QuarkusProcessTimeoutSweeper() {
            @Override
            protected void runInNewTransaction(final Runnable action) {
                action.run();
            }
        };
        testee.repository = repository;
        testee.config = config;
        testee.handlers = handlers;
    }

    private static DueProcessTimeout due(final String id) {
        return new DueProcessTimeout(id, "OrderProcess", 1, 1000L, "await-ack", 0);
    }

    @Test
    public void testDueTimeoutIsHandledAndDeleted() {
        // PREPARE
        when(handlers.isResolvable()).thenReturn(true);
        when(handlers.get()).thenReturn(handler);
        when(repository.fetchDue(anyLong(), eq(100))).thenReturn(List.of(due("p-1")));

        // TEST
        testee.drain();

        // VERIFY
        verify(handler).onTimeout(due("p-1"));
        verify(repository).delete("p-1");
        verify(repository, never()).recordFailure(anyString(), anyString(), anyInt());
    }

    @Test
    public void testFailingHandlerIsRecorded() {
        // PREPARE
        when(handlers.isResolvable()).thenReturn(true);
        when(handlers.get()).thenReturn(handler);
        when(repository.fetchDue(anyLong(), eq(100))).thenReturn(List.of(due("p-1")));
        doThrow(new RuntimeException("boom")).when(handler).onTimeout(due("p-1"));

        // TEST
        testee.drain();

        // VERIFY
        verify(repository).recordFailure("p-1", "boom", 5);
        verify(repository, never()).delete(anyString());
    }

    @Test
    public void testNoHandlerSkipsSweep() {
        // PREPARE: no unique handler registered
        when(handlers.isResolvable()).thenReturn(false);

        // TEST
        testee.drain();

        // VERIFY
        verify(repository, never()).fetchDue(anyLong(), anyInt());
    }

    @Test
    public void testEmptyQueueDoesNothing() {
        // PREPARE
        when(handlers.isResolvable()).thenReturn(true);
        when(handlers.get()).thenReturn(handler);
        when(repository.fetchDue(anyLong(), eq(100))).thenReturn(List.of());

        // TEST
        testee.drain();

        // VERIFY
        verify(repository, never()).delete(anyString());
    }

}
