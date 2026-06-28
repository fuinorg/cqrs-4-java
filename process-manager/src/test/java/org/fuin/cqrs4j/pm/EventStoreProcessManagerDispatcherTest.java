package org.fuin.cqrs4j.pm;

import org.fuin.cqrs4j.core.Command;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.ddd4j.core.DomainEvent;
import org.fuin.ddd4j.core.Event;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventId;
import org.fuin.esc.api.SimpleCommonEvent;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.TenantId;
import org.fuin.esc.api.TypeName;
import org.fuin.esc.mem.InMemoryEventStoreAsync;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for {@link EventStoreProcessManagerDispatcher}: a real {@link InMemoryEventStoreAsync}
 * subscription drives the sample process manager, whose commands are dispatched through a
 * {@link SimpleCommandBus}. The {@code ReserveStock} handler appends the follow-up event to the same feed
 * stream, so the whole flow runs automatically through the subscription.
 */
class EventStoreProcessManagerDispatcherTest {

    /** In-memory stand-in for an EventStoreDB category/event-type projection. */
    private static final StreamId FEED = new SimpleStreamId("order-events");

    private InMemoryEventStoreAsync eventStore;

    private SampleProcessManagerRepository repository;

    private EventStoreProcessManagerDispatcher<SampleProcessId, SampleProcessManager> dispatcher;

    private final List<Command> sentCommands = new CopyOnWriteArrayList<>();

    private volatile boolean stockUnavailable;

    @BeforeEach
    void setup() {
        eventStore = new InMemoryEventStoreAsync(Executors.newCachedThreadPool());
        eventStore.open();
        repository = new SampleProcessManagerRepository(eventStore);

        final SimpleCommandBus commandBus = new SimpleCommandBus();
        // The ReserveStock handler simulates the stock service: it emits the follow-up event onto the feed.
        commandBus.register(ReserveStockCommand.class, cmd -> {
            sentCommands.add(cmd);
            final DomainEvent<?> followUp = stockUnavailable
                    ? new StockUnavailableEvent(cmd.getOrderId())
                    : new StockReservedEvent(cmd.getOrderId());
            eventStore.appendToStream(FEED, asCommonEvent(followUp)).join();
        });
        commandBus.register(ConfirmOrderCommand.class, sentCommands::add);
        commandBus.register(CancelOrderCommand.class, sentCommands::add);

        // Correlate an event to the process manager: the event's entity id IS the process id.
        final Function<Event, Optional<SampleProcessId>> correlation = event -> {
            if (event instanceof DomainEvent<?> de && de.getEntityId() instanceof SampleProcessId pid) {
                return Optional.of(pid);
            }
            return Optional.empty();
        };

        dispatcher = new EventStoreProcessManagerDispatcher<>(eventStore, repository, commandBus, correlation,
                List.of(FEED), 0);
        // Note: the subscription is started in each test *after* the first event was appended, because the
        // in-memory store (unlike EventStoreDB) only allows subscribing to an already existing stream.
    }

    @AfterEach
    void teardown() {
        dispatcher.stop().join();
        eventStore.close();
    }

    @Test
    void testHappyPath() {

        // PREPARE
        final SampleProcessId id = new SampleProcessId();
        stockUnavailable = false;

        // TEST
        eventStore.appendToStream(FEED, asCommonEvent(new OrderPlacedEvent(id))).join();
        dispatcher.start().join(); // catch-up from 0 replays the OrderPlaced event

        // VERIFY
        await(() -> statusOf(id) == SampleProcessManager.Status.COMPLETED);
        assertThat(statusOf(id)).isEqualTo(SampleProcessManager.Status.COMPLETED);
        assertThat(sentCommands).anyMatch(ConfirmOrderCommand.class::isInstance);
    }

    @Test
    void testCompensation() {

        // PREPARE
        final SampleProcessId id = new SampleProcessId();
        stockUnavailable = true;

        // TEST
        eventStore.appendToStream(FEED, asCommonEvent(new OrderPlacedEvent(id))).join();
        dispatcher.start().join(); // catch-up from 0 replays the OrderPlaced event

        // VERIFY
        await(() -> statusOf(id) == SampleProcessManager.Status.COMPENSATED);
        assertThat(statusOf(id)).isEqualTo(SampleProcessManager.Status.COMPENSATED);
        assertThat(sentCommands).anyMatch(CancelOrderCommand.class::isInstance);
    }

    private SampleProcessManager.Status statusOf(final SampleProcessId id) {
        try {
            return repository.read(id).join().getStatus();
        } catch (final CompletionException ex) {
            if (ex.getCause() instanceof AggregateNotFoundException) {
                return SampleProcessManager.Status.NEW; // not created yet
            }
            throw ex;
        }
    }

    private static CommonEvent asCommonEvent(final DomainEvent<?> event) {
        return new SimpleCommonEvent(new EventId(event.getEventId().asBaseType()),
                new TypeName(event.getEventType().asBaseType()), event, (TenantId) null);
    }

    private static void await(final BooleanSupplier condition) {
        final long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }
    }

}
