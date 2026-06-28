# cqrs-4-java-pm

Reusable building blocks for **event-sourced process managers** on top of [cqrs-4-java](../).

A *process manager* coordinates a long-running process: it **reacts to domain events** (received through an
asynchronous, subscribable event store) by **issuing commands**, and it keeps its own state — which is itself
**event-sourced** (rebuilt from its own events). It never executes domain logic directly; it only decides
*what should happen next* and emits commands for that.

> This module deliberately uses the term **process manager** only (never "saga").

## Dependency

Versions are managed by the [cqrs-4-java-bom](../bom):

```xml
<dependency>
    <groupId>org.fuin.cqrs4j</groupId>
    <artifactId>cqrs-4-java-pm</artifactId>
</dependency>
```

The module builds on the **asynchronous** event store API
(`org.fuin.esc.api.EventStoreAsync` / `SubscribableEventStoreAsync`), `cqrs-4-java-core`, and the ddd4j async
event-sourcing repository (`org.fuin.ddd4j.esc.EventStoreRepositoryAsync` from `ddd-4-java-esc`) — not on any
concrete event store implementation.

## Building blocks

The `ProcessManager` and `CommandBus` interfaces live in [cqrs-4-java-core](../core); the implementations
below live in this module.

| Type | Responsibility |
|------|----------------|
| `ProcessManager` *(core)* | Contract: `handle(Event)` to react, plus a buffer of `getCommandsToSend()` / `clearCommandsToSend()`. |
| `AbstractProcessManager<ID>` | Event-sourced base (extends ddd4j `AbstractAggregateRoot`). React by `apply(<state event>)` and `send(<command>)`. |
| `CommandBus` *(core)* / `SimpleCommandBus` | Dispatches a produced command to whatever executes it (routes by command class). |
| `EventStoreRepositoryAsync<ID, PM>` *(ddd4j)* | Loads/saves a process manager **asynchronously** (event-sourced, one aggregate stream per instance). Subclass it per process-manager type. |
| `EventStoreProcessManagerDispatcher<ID, PM>` | The driver: subscribes to stream(s) via `SubscribableEventStoreAsync`, correlates each event to a process manager, loads it, lets it react, saves it and dispatches the resulting commands. |

## How it works

```
            subscribeToStream(...)
domain event ───────────────────────▶ Dispatcher
                                          │ correlation(event) -> process manager id
                                          │ repository.read(id)     (or create() on the first event)
                                          │ pm.handle(event)        (apply state event + buffer commands)
                                          │ repository.update(pm)   (append the new state events)
                                          ▼ commandBus.send(cmd)    (for each buffered command)
                                       command handler
```

The full loop is **event → process manager → command → handler → (new event) → process manager**. A command
handler typically triggers the next step, whose resulting event flows back through the subscription.

## Usage

```java
// 1. A process manager (its own state is rebuilt from ProcessXxxEvent)
final class OrderProcessManager extends AbstractProcessManager<OrderProcessId> {
    private Status status = Status.NEW;

    @Override
    public void handle(Event event) {
        if (event instanceof OrderPlacedEvent e) {
            apply(new ProcessStartedEvent(e.getEntityId(), getNextApplyVersion()));
            send(new ReserveStockCommand(e.getEntityId()));
        } else if (event instanceof StockReservedEvent e) {
            apply(new ProcessCompletedEvent(e.getEntityId(), getNextApplyVersion()));
            send(new ConfirmOrderCommand(e.getEntityId()));
        }
    }

    @ApplyEvent protected void onEvent(ProcessStartedEvent e)   { status = Status.STARTED; }
    @ApplyEvent protected void onEvent(ProcessCompletedEvent e) { status = Status.COMPLETED; }
    // getId() / getType() ...
}

// 2. A repository for the process manager (a ddd4j EventStoreRepositoryAsync subclass)
final class OrderProcessManagerRepository extends EventStoreRepositoryAsync<OrderProcessId, OrderProcessManager> {
    OrderProcessManagerRepository(EventStoreAsync es) { super(es); }
    @Override public Class<OrderProcessManager> getAggregateClass() { return OrderProcessManager.class; }
    @Override public EntityType getAggregateType() { return OrderProcessId.TYPE; }
    @Override public OrderProcessManager create() { return new OrderProcessManager(); }
    @Override protected String getIdParamName() { return "orderProcessId"; }
}

// 3. Wiring (here against the in-memory async store used for testing)
var eventStore = new InMemoryEventStoreAsync(Executors.newCachedThreadPool());
eventStore.open();

var repository = new OrderProcessManagerRepository(eventStore);

var commandBus = new SimpleCommandBus();
commandBus.register(ReserveStockCommand.class, cmd -> stockService.reserve(cmd));
commandBus.register(ConfirmOrderCommand.class, cmd -> orderService.confirm(cmd));

// Map an incoming event to the process manager that should react to it
Function<Event, Optional<OrderProcessId>> correlation = e ->
        (e instanceof DomainEvent<?> de && de.getEntityId() instanceof OrderProcessId id)
                ? Optional.of(id) : Optional.empty();

var dispatcher = new EventStoreProcessManagerDispatcher<>(
        eventStore, repository, commandBus, correlation,
        List.of(new SimpleStreamId("order-events")), 0L);
dispatcher.start();
// ... dispatcher.stop() on shutdown
```

## Notes

- **Asynchronous throughout.** Reads, appends and subscriptions return `CompletableFuture`; the dispatcher
  drives one process manager per delivered event and keeps per-stream ordering.
- **State is event-sourced** through the same event store, so a process manager can be rebuilt at any time by
  replaying its stream (the repository's `read`); a process manager not yet persisted is created on its first
  event.
- **Which streams to subscribe to** is up to the caller. Against EventStoreDB you would typically subscribe to
  a category / event-type projection; the in-memory store has no such projection, so feed the relevant events
  onto a dedicated stream that the dispatcher subscribes to (see the module tests).
- **In-memory store caveat:** `InMemoryEventStoreAsync.subscribeToStream` only accepts an already existing
  stream (EventStoreDB is more lenient), which matters mostly for tests.
- A process manager instance is **single threaded** (`@NotThreadSafe`) — load, use and save it within one
  thread; the surrounding repository, command bus and dispatcher are thread safe.

See the module tests (`src/test`) for a complete, runnable order-fulfilment example (happy path and
compensation) wired against `InMemoryEventStoreAsync`.
