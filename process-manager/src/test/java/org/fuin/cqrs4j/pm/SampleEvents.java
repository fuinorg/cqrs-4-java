package org.fuin.cqrs4j.pm;

import org.fuin.ddd4j.core.AggregateVersion;
import org.fuin.ddd4j.core.DomainEvent;
import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.utils4j.TestOmitted;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Minimal hand-rolled {@link DomainEvent} base for the sample (avoids pulling a serialization module into the
 * process-manager module just for tests). The in-memory event store keeps the object as-is, so nothing is
 * actually serialized.
 *
 * @param <ID> Entity id type.
 */
@TestOmitted("Sample base domain event")
abstract class AbstractSampleDomainEvent<ID extends EntityId> implements DomainEvent<ID>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final EventId eventId = new EventId();

    private final ZonedDateTime timestamp = ZonedDateTime.now();

    private final ID entityId;

    private final AggregateVersion version;

    private final EventType type;

    AbstractSampleDomainEvent(final ID entityId, final AggregateVersion version, final EventType type) {
        this.entityId = entityId;
        this.version = version;
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

    @Override
    public EntityIdPath getEntityIdPath() {
        return new EntityIdPath(entityId);
    }

    @Override
    public ID getEntityId() {
        return entityId;
    }

    @Override
    public AggregateVersion getAggregateVersion() {
        return version;
    }

    @Override
    public Integer getAggregateVersionInteger() {
        return version.asBaseType();
    }

}

/** External event: an order was placed (triggers the process). */
@TestOmitted("Sample event")
final class OrderPlacedEvent extends AbstractSampleDomainEvent<SampleProcessId> {
    @Serial
    private static final long serialVersionUID = 1L;
    static final EventType TYPE = new EventType("OrderPlacedEvent");
    OrderPlacedEvent(final SampleProcessId id) {
        super(id, new AggregateVersion(0), TYPE);
    }
}

/** External event: stock could be reserved (success path). */
@TestOmitted("Sample event")
final class StockReservedEvent extends AbstractSampleDomainEvent<SampleProcessId> {
    @Serial
    private static final long serialVersionUID = 1L;
    static final EventType TYPE = new EventType("StockReservedEvent");
    StockReservedEvent(final SampleProcessId id) {
        super(id, new AggregateVersion(0), TYPE);
    }
}

/** External event: stock was unavailable (compensation path). */
@TestOmitted("Sample event")
final class StockUnavailableEvent extends AbstractSampleDomainEvent<SampleProcessId> {
    @Serial
    private static final long serialVersionUID = 1L;
    static final EventType TYPE = new EventType("StockUnavailableEvent");
    StockUnavailableEvent(final SampleProcessId id) {
        super(id, new AggregateVersion(0), TYPE);
    }
}

/** Process manager state event: the process started. */
@TestOmitted("Sample event")
final class ProcessStartedEvent extends AbstractSampleDomainEvent<SampleProcessId> {
    @Serial
    private static final long serialVersionUID = 1L;
    static final EventType TYPE = new EventType("ProcessStartedEvent");
    ProcessStartedEvent(final SampleProcessId id, final AggregateVersion version) {
        super(id, version, TYPE);
    }
}

/** Process manager state event: the process completed successfully. */
@TestOmitted("Sample event")
final class ProcessCompletedEvent extends AbstractSampleDomainEvent<SampleProcessId> {
    @Serial
    private static final long serialVersionUID = 1L;
    static final EventType TYPE = new EventType("ProcessCompletedEvent");
    ProcessCompletedEvent(final SampleProcessId id, final AggregateVersion version) {
        super(id, version, TYPE);
    }
}

/** Process manager state event: the process was compensated. */
@TestOmitted("Sample event")
final class ProcessCompensatedEvent extends AbstractSampleDomainEvent<SampleProcessId> {
    @Serial
    private static final long serialVersionUID = 1L;
    static final EventType TYPE = new EventType("ProcessCompensatedEvent");
    ProcessCompensatedEvent(final SampleProcessId id, final AggregateVersion version) {
        super(id, version, TYPE);
    }
}
