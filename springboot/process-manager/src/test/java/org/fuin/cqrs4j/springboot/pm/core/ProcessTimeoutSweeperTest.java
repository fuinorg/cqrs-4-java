package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.cqrs4j.core.ProcessTimeoutHandler;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler.DueProcessTimeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link ProcessTimeoutSweeper} class.
 */
@ExtendWith(MockitoExtension.class)
class ProcessTimeoutSweeperTest {

    @Mock
    private ProcessTimeoutRepository repository;

    @Mock
    private ProcessTimeoutHandler handler;

    @Mock
    private ObjectProvider<ProcessTimeoutHandler> handlers;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ProcessTimeoutSweeper newSweeper(final int maxRetries) {
        final ProcessTimeoutConfig config = new ProcessTimeoutConfig("*/5 * * * * *", 100, maxRetries);
        return new ProcessTimeoutSweeper(repository, config, handlers, transactionManager);
    }

    private static DueProcessTimeout due(final String id) {
        return new DueProcessTimeout(id, "OrderProcess", 1, 1000L, "await-ack", 0);
    }

    @Test
    void testDueTimeoutIsHandledAndDeleted() {
        // PREPARE
        final ProcessTimeoutSweeper testee = newSweeper(5);
        when(handlers.getIfUnique()).thenReturn(handler);
        when(repository.fetchDue(anyLong(), eq(100))).thenReturn(List.of(due("p-1")));

        // TEST
        testee.drain();

        // VERIFY
        verify(handler).onTimeout(due("p-1"));
        verify(repository).delete("p-1");
        verify(repository, never()).recordFailure(anyString(), anyString(), anyInt());
    }

    @Test
    void testFailingHandlerIsRecorded() {
        // PREPARE
        final ProcessTimeoutSweeper testee = newSweeper(5);
        when(handlers.getIfUnique()).thenReturn(handler);
        when(repository.fetchDue(anyLong(), eq(100))).thenReturn(List.of(due("p-1")));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(handler).onTimeout(due("p-1"));

        // TEST
        testee.drain();

        // VERIFY
        verify(repository).recordFailure("p-1", "boom", 5);
        verify(repository, never()).delete(anyString());
    }

    @Test
    void testOneFailureDoesNotStopOthers() {
        // PREPARE
        final ProcessTimeoutSweeper testee = newSweeper(5);
        when(handlers.getIfUnique()).thenReturn(handler);
        when(repository.fetchDue(anyLong(), eq(100))).thenReturn(List.of(due("p-1"), due("p-2")));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(handler).onTimeout(due("p-1"));

        // TEST
        testee.drain();

        // VERIFY
        verify(repository).recordFailure(eq("p-1"), anyString(), eq(5));
        verify(repository).delete("p-2");
    }

    @Test
    void testNoHandlerSkipsSweep() {
        // PREPARE: no unique handler registered
        final ProcessTimeoutSweeper testee = newSweeper(5);
        when(handlers.getIfUnique()).thenReturn(null);

        // TEST
        testee.drain();

        // VERIFY: nothing is read or handled
        verify(repository, never()).fetchDue(anyLong(), anyInt());
    }

    @Test
    void testEmptyQueueDoesNothing() {
        // PREPARE
        final ProcessTimeoutSweeper testee = newSweeper(5);
        when(handlers.getIfUnique()).thenReturn(handler);
        when(repository.fetchDue(anyLong(), eq(100))).thenReturn(List.of());

        // TEST
        testee.drain();

        // VERIFY
        verify(repository, never()).delete(anyString());
        verify(repository, never()).recordFailure(anyString(), anyString(), anyInt());
    }

}
