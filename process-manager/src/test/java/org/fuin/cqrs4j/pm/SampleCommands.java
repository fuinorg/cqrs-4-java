package org.fuin.cqrs4j.pm;

import org.fuin.cqrs4j.core.Command;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.utils4j.TestOmitted;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

/**
 * Minimal {@link Command} base for the sample commands.
 */
@TestOmitted("Sample base command")
abstract class AbstractSampleCommand implements Command {

    private final EventId eventId = new EventId();

    private final ZonedDateTime timestamp = ZonedDateTime.now();

    private final EventType type;

    private final SampleProcessId orderId;

    AbstractSampleCommand(final SampleProcessId orderId, final EventType type) {
        this.orderId = orderId;
        this.type = type;
    }

    @Override
    public EventId getEventId() {
        return eventId;
    }

    @Override
    public EventType getEventType() {
        return type;
    }

    @Override
    public ZonedDateTime getEventTimestamp() {
        return timestamp;
    }

    @Override
    @Nullable
    public EventId getCorrelationId() {
        return null;
    }

    @Override
    @Nullable
    public EventId getCausationId() {
        return null;
    }

    SampleProcessId getOrderId() {
        return orderId;
    }

}

/** Reserve stock for the order. */
@TestOmitted("Sample command")
final class ReserveStockCommand extends AbstractSampleCommand {
    static final EventType TYPE = new EventType("ReserveStockCommand");
    ReserveStockCommand(final SampleProcessId orderId) {
        super(orderId, TYPE);
    }
}

/** Confirm the order (success path). */
@TestOmitted("Sample command")
final class ConfirmOrderCommand extends AbstractSampleCommand {
    static final EventType TYPE = new EventType("ConfirmOrderCommand");
    ConfirmOrderCommand(final SampleProcessId orderId) {
        super(orderId, TYPE);
    }
}

/** Cancel the order (compensation path). */
@TestOmitted("Sample command")
final class CancelOrderCommand extends AbstractSampleCommand {
    static final EventType TYPE = new EventType("CancelOrderCommand");
    CancelOrderCommand(final SampleProcessId orderId) {
        super(orderId, TYPE);
    }
}
