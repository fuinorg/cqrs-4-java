package org.fuin.cqrs4j.springboot.test.pm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.fuin.cqrs4j.core.Command;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;

import java.time.ZonedDateTime;

/**
 * Command produced by the {@link SampleProcessManagerView} and delivered via the command outbox. Only
 * the {@code message} payload is serialized; the event metadata is ignored to keep the JSON simple.
 */
public final class SampleNotifyCommand implements Command {

    /** Unique type name (used as the {@code /cmd/{type}} path variable). */
    public static final EventType TYPE = new EventType("SampleNotifyCommand");

    @JsonIgnore
    private final EventId eventId = new EventId();

    @JsonProperty("message")
    private final String message;

    /**
     * Constructor with mandatory data.
     *
     * @param message Message to deliver.
     */
    public SampleNotifyCommand(final String message) {
        this.message = message;
    }

    /**
     * Returns the message.
     *
     * @return Message.
     */
    public String getMessage() {
        return message;
    }

    @Override
    @JsonIgnore
    public EventId getEventId() {
        return eventId;
    }

    @Override
    @JsonIgnore
    public EventType getEventType() {
        return TYPE;
    }

    @Override
    @JsonIgnore
    public ZonedDateTime getEventTimestamp() {
        return ZonedDateTime.parse("2020-01-01T00:00:00Z");
    }

    @Override
    @JsonIgnore
    public EventId getCorrelationId() {
        return null;
    }

    @Override
    @JsonIgnore
    public EventId getCausationId() {
        return null;
    }

}
