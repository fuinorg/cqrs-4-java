package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandOverloadedException;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link BulkheadProcessedCommandStore}.
 */
public class BulkheadProcessedCommandStoreTest {

    private static final Duration NO_WAIT = Duration.ZERO;

    @Test
    public void testPassesThroughWhenThereIsCapacity() {

        // PREPARE
        final RecordingStore delegate = new RecordingStore();
        final BulkheadProcessedCommandStore testee = new BulkheadProcessedCommandStore(delegate, 2, NO_WAIT);

        // TEST & VERIFY
        assertThat(testee.processed("cmd-1")).isFalse();
        delegate.markProcessed("cmd-1");
        assertThat(testee.processed("cmd-1")).isTrue();
    }

    @Test
    public void testRefusesTheCommandWhenTheBulkheadIsFull() throws Exception {

        // PREPARE: one slot, held by a call that is parked inside the delegate
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final BlockingStore delegate = new BlockingStore(inside, release);
        final BulkheadProcessedCommandStore testee = new BulkheadProcessedCommandStore(delegate, 1, NO_WAIT);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> testee.processed("cmd-slow"));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

            // TEST & VERIFY: the second command is turned away instead of queueing behind the slow lookup
            assertThatThrownBy(() -> testee.processed("cmd-2"))
                    .isInstanceOf(CommandOverloadedException.class)
                    .hasMessageContaining("as many commands as it is allowed to");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testRecordingIsNeverRefused() throws Exception {

        // PREPARE: the bulkhead is full, exactly as above
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final BlockingStore delegate = new BlockingStore(inside, release);
        final BulkheadProcessedCommandStore testee = new BulkheadProcessedCommandStore(delegate, 1, NO_WAIT);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> testee.processed("cmd-slow"));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

            // TEST: a handler that already succeeded records its command
            testee.markProcessed("cmd-done");

            // VERIFY: it got through. Shedding it would leave the command executed but unrecorded, and the
            // next redelivery would execute it a second time.
            assertThat(delegate.recorded()).containsExactly("cmd-done");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testInvalidArguments() {
        assertThatThrownBy(() -> new BulkheadProcessedCommandStore(null, 1, NO_WAIT))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BulkheadProcessedCommandStore(new RecordingStore(), 0, NO_WAIT))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BulkheadProcessedCommandStore(new RecordingStore(), 1, null))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * Store that remembers what was recorded.
     */
    private static class RecordingStore implements ProcessedCommandStore {

        private final List<String> recorded = new CopyOnWriteArrayList<>();

        @Override
        public boolean processed(final String commandId) {
            return recorded.contains(commandId);
        }

        @Override
        public void markProcessed(final String commandId) {
            recorded.add(commandId);
        }

        List<String> recorded() {
            return List.copyOf(recorded);
        }

    }

    /**
     * Store whose lookup parks until it is released, which is what a database that stopped answering looks
     * like from the caller's side.
     */
    private static final class BlockingStore extends RecordingStore {

        private final CountDownLatch inside;

        private final CountDownLatch release;

        private final AtomicInteger lookups = new AtomicInteger();

        private BlockingStore(final CountDownLatch inside, final CountDownLatch release) {
            this.inside = inside;
            this.release = release;
        }

        @Override
        public boolean processed(final String commandId) {
            lookups.incrementAndGet();
            inside.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return false;
        }

    }

}
