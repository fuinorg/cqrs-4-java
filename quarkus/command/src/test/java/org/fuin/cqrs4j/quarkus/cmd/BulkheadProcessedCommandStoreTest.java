package org.fuin.cqrs4j.quarkus.cmd;

import io.smallrye.faulttolerance.api.Guard;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.fuin.cqrs4j.core.CommandOverloadedException;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link BulkheadProcessedCommandStore}.
 * <p>
 * A real {@link Guard} cannot be built here: it needs the SmallRye Fault Tolerance runtime SPI, which only
 * exists inside the container ({@code NoClassDefFoundError: SpiAccess$Holder}). The guard is therefore
 * supplied by the test, which is enough to pin what this class is responsible for - translating a full
 * bulkhead into a {@link CommandOverloadedException} and keeping the record path out of the guard entirely.
 * The bulkhead itself is SmallRye's and is not re-tested here.
 */
public class BulkheadProcessedCommandStoreTest {

    @Test
    public void testPassesThroughWhenThereIsCapacity() {

        // PREPARE
        final RecordingStore delegate = new RecordingStore();
        final BulkheadProcessedCommandStore testee =
                new BulkheadProcessedCommandStore(delegate, new PassThroughGuard());

        // TEST & VERIFY
        assertThat(testee.processed("cmd-1")).isFalse();
        delegate.markProcessed("cmd-1");
        assertThat(testee.processed("cmd-1")).isTrue();
    }

    @Test
    public void testRefusesTheCommandWhenTheBulkheadIsFull() {

        // PREPARE
        final RecordingStore delegate = new RecordingStore();
        final BulkheadProcessedCommandStore testee =
                new BulkheadProcessedCommandStore(delegate, new FullGuard());

        // TEST & VERIFY
        assertThatThrownBy(() -> testee.processed("cmd-1"))
                .isInstanceOf(CommandOverloadedException.class)
                .hasMessageContaining("as many commands as it is allowed to")
                .hasCauseInstanceOf(BulkheadException.class);
    }

    @Test
    public void testRecordingIsNeverRefused() {

        // PREPARE: a guard that refuses everything it is asked to run
        final RecordingStore delegate = new RecordingStore();
        final BulkheadProcessedCommandStore testee =
                new BulkheadProcessedCommandStore(delegate, new FullGuard());

        // TEST
        testee.markProcessed("cmd-done");

        // VERIFY: it got through. Shedding it would leave the command executed but unrecorded, and the next
        // redelivery would execute it a second time.
        assertThat(delegate.recorded()).containsExactly("cmd-done");
    }

    @Test
    public void testInvalidArguments() {
        assertThatThrownBy(() -> new BulkheadProcessedCommandStore(null, new PassThroughGuard()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BulkheadProcessedCommandStore(new RecordingStore(), (Guard) null))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * Store that remembers what was recorded.
     */
    private static final class RecordingStore implements ProcessedCommandStore {

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
     * Guard that simply runs what it is given.
     */
    private static class PassThroughGuard implements Guard {

        @Override
        public <T> T call(final Callable<T> action, final Class<T> type) throws Exception {
            return action.call();
        }

        @Override
        public <T> T call(final Callable<T> action, final TypeLiteral<T> type) throws Exception {
            return action.call();
        }

        @Override
        public <T> T get(final Supplier<T> action, final Class<T> type) {
            return action.get();
        }

        @Override
        public <T> T get(final Supplier<T> action, final TypeLiteral<T> type) {
            return action.get();
        }

    }

    /**
     * Guard whose bulkhead is full, so nothing it is given ever runs.
     */
    private static final class FullGuard extends PassThroughGuard {

        @Override
        public <T> T call(final Callable<T> action, final Class<T> type) {
            throw new BulkheadException("Bulkhead is full");
        }

    }

}
