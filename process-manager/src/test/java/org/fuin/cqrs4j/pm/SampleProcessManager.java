package org.fuin.cqrs4j.pm;

import org.fuin.ddd4j.core.ApplyEvent;
import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.core.Event;
import org.fuin.utils4j.TestOmitted;
import org.jspecify.annotations.Nullable;

/**
 * Sample event-sourced process manager coordinating a tiny "order fulfilment" process:
 * <pre>
 *   OrderPlaced     -&gt; (start)       send ReserveStock
 *   StockReserved   -&gt; (complete)    send ConfirmOrder
 *   StockUnavailable-&gt; (compensate)  send CancelOrder
 * </pre>
 */
@TestOmitted("Sample process manager")
final class SampleProcessManager extends AbstractProcessManager<SampleProcessId> {

    enum Status {
        NEW, STARTED, COMPLETED, COMPENSATED
    }

    @Nullable
    private SampleProcessId id;

    private Status status = Status.NEW;

    @Override
    public void handle(final Event event) {
        if (event instanceof OrderPlacedEvent ev) {
            apply(new ProcessStartedEvent(ev.getEntityId(), getNextApplyVersion()));
            send(new ReserveStockCommand(ev.getEntityId()));
        } else if (event instanceof StockReservedEvent ev) {
            apply(new ProcessCompletedEvent(ev.getEntityId(), getNextApplyVersion()));
            send(new ConfirmOrderCommand(ev.getEntityId()));
        } else if (event instanceof StockUnavailableEvent ev) {
            apply(new ProcessCompensatedEvent(ev.getEntityId(), getNextApplyVersion()));
            send(new CancelOrderCommand(ev.getEntityId()));
        }
    }

    @ApplyEvent
    protected void onEvent(final ProcessStartedEvent event) {
        this.id = event.getEntityId();
        this.status = Status.STARTED;
    }

    @ApplyEvent
    protected void onEvent(final ProcessCompletedEvent event) {
        this.status = Status.COMPLETED;
    }

    @ApplyEvent
    protected void onEvent(final ProcessCompensatedEvent event) {
        this.status = Status.COMPENSATED;
    }

    @Override
    public EntityType getType() {
        return SampleProcessId.TYPE;
    }

    @Override
    public SampleProcessId getId() {
        return id;
    }

    Status getStatus() {
        return status;
    }

}
