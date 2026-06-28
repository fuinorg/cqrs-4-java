package org.fuin.cqrs4j.pm;

import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.esc.mem.InMemoryEventStoreAsync;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test that a process manager can be persisted and rebuilt through the ddd4j
 * {@link org.fuin.ddd4j.esc.EventStoreRepositoryAsync} (via {@link SampleProcessManagerRepository}).
 */
class SampleProcessManagerRepositoryTest {

    private InMemoryEventStoreAsync eventStore;

    private SampleProcessManagerRepository repository;

    @BeforeEach
    void setup() {
        eventStore = new InMemoryEventStoreAsync(Executors.newCachedThreadPool());
        eventStore.open();
        repository = new SampleProcessManagerRepository(eventStore);
    }

    @AfterEach
    void teardown() {
        eventStore.close();
    }

    @Test
    void testReadUnknownFailsWithNotFound() {
        assertThatThrownBy(() -> repository.read(new SampleProcessId()).join())
                .hasCauseInstanceOf(AggregateNotFoundException.class);
    }

    @Test
    void testUpdateAndReadRoundtrip() {

        // PREPARE - a fresh process manager that reacted to its first event
        final SampleProcessId id = new SampleProcessId();
        final SampleProcessManager pm = new SampleProcessManager();
        pm.handle(new OrderPlacedEvent(id)); // applies ProcessStarted

        // TEST
        repository.update(pm).join();
        final SampleProcessManager reloaded = repository.read(id).join();

        // VERIFY - event-sourced state and version were restored
        assertThat(reloaded.getStatus()).isEqualTo(SampleProcessManager.Status.STARTED);
        assertThat(reloaded.getId()).isEqualTo(id);
        assertThat(reloaded.getVersion()).isEqualTo(pm.getVersion());
    }

}
