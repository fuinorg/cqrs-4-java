package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.Jsonb;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.jsonb.AbstractEvent;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.cqrs4j.jsonb.TestUtils.jsonb;
import static org.fuin.utils4j.Utils4J.deserialize;
import static org.fuin.utils4j.Utils4J.serialize;

public class AbstractCommandTest {

    private static final EventType MY_EVENT_TYPE = new EventType("MyEvent");

    private static final EventType MY_COMMAND_TYPE = new EventType("MyCommand");

    @Test
    public final void testConstructorDefault() {

        // TEST
        final AbstractCommand testee = new MyCommand();

        // VERIFY
        assertThat(testee.getEventId()).isNotNull();
        assertThat(testee.getEventTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isNull();
        assertThat(testee.getCorrelationId()).isNull();
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testConstructorEvent() {

        // PREPARE
        final EventId correlationId = new EventId();
        final EventId causationId = new EventId();
        final MyEvent event = new MyEvent(correlationId, causationId);

        // TEST
        final AbstractCommand testee = new MyCommand(event);

        // VERIFY
        assertThat(testee.getEventId()).isNotNull();
        assertThat(testee.getEventTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isEqualTo(event.getEventId());
        assertThat(testee.getCorrelationId()).isEqualTo(correlationId);
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testSerializeDeserialize() {

        // PREPARE
        final EventId correlationId = new EventId();
        final EventId causationId = new EventId();
        final MyCommand original = new MyCommand(correlationId, causationId);

        // TEST
        final MyCommand copy = deserialize(serialize(original));

        // VERIFY
        assertThat(copy).isEqualTo(original);
        assertThat(copy.getCausationId()).isEqualTo(original.getCausationId());
        assertThat(copy.getCorrelationId()).isEqualTo(original.getCorrelationId());
        assertThat(copy.getEventId()).isEqualTo(original.getEventId());
        assertThat(copy.getEventType()).isEqualTo(original.getEventType());
        assertThat(copy.getEventTimestamp()).isEqualTo(original.getEventTimestamp());

    }

    @Test
    public final void testMarshalUnmarshal() throws Exception {

        try (final Jsonb jsonb = jsonb()) {

            // PREPARE
            final EventId correlationId = new EventId();
            final EventId causationId = new EventId();
            final MyCommand original = new MyCommand(correlationId, causationId);

            // TEST
            final String json = jsonb.toJson(original, MyCommand.class);
            final MyCommand copy = jsonb.fromJson(json, MyCommand.class);

            // VERIFY
            assertThat(copy).isEqualTo(original);
            assertThat(copy.getCausationId()).isEqualTo(original.getCausationId());
            assertThat(copy.getCorrelationId()).isEqualTo(original.getCorrelationId());
            assertThat(copy.getEventId()).isEqualTo(original.getEventId());
            assertThat(copy.getEventType()).isEqualTo(original.getEventType());
            assertThat(copy.getEventTimestamp()).isEqualTo(original.getEventTimestamp());

        }

    }

    @Test
    public final void testUnmarshal() throws Exception {

        try (final Jsonb jsonb = jsonb()) {

            // PREPARE
            final String originalJson = """
                    {
                      "event-id" : "f910c6d7-debc-46e1-ae02-9ca6f4658cf5",
                      "event-timestamp" : "2016-09-18T10:38:08.0+02:00[Europe/Berlin]",
                      "correlation-id" : "2a5893a9-00da-4003-b280-98324eccdef1",
                      "causation-id" : "f13d3481-51b7-423f-8fe7-5c342f7d7c46"
                    }
                    """;

            // TEST
            final MyCommand copy = jsonb.fromJson(originalJson, MyCommand.class);

            // VERIFY
            assertThat(copy.getCausationId()).isEqualTo(new EventId(UUID.fromString("f13d3481-51b7-423f-8fe7-5c342f7d7c46")));
            assertThat(copy.getCorrelationId()).isEqualTo(new EventId(UUID.fromString("2a5893a9-00da-4003-b280-98324eccdef1")));
            assertThat(copy.getEventId()).isEqualTo(new EventId(UUID.fromString("f910c6d7-debc-46e1-ae02-9ca6f4658cf5")));
            assertThat(copy.getEventType()).isEqualTo(copy.getEventType());
            assertThat(copy.getEventTimestamp()).isEqualTo(ZonedDateTime.of(2016, 9, 18, 10, 38, 8, 0, ZoneId.of("Europe/Berlin")));

        }

    }

    public static class MyCommand extends AbstractCommand {

        private static final long serialVersionUID = 1L;

        public MyCommand() {
            super();
        }

        public MyCommand(Event respondTo) {
            super(respondTo);
        }

        public MyCommand(EventId correlationId, EventId causationId) {
            super(correlationId, causationId);
        }

        @Override
        public EventType getEventType() {
            return MY_COMMAND_TYPE;
        }

    }

    public static class MyEvent extends AbstractEvent {

        private static final long serialVersionUID = 1L;

        public MyEvent(EventId correlationId, EventId causationId) {
            super(correlationId, causationId);
        }

        @Override
        public EventType getEventType() {
            return MY_EVENT_TYPE;
        }

    }

}
