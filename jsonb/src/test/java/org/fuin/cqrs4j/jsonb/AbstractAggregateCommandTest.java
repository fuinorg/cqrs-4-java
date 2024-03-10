package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.Jsonb;
import jakarta.validation.Validation;
import org.fuin.ddd4j.core.AggregateVersion;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.jsonb.AbstractEvent;
import org.fuin.objects4j.common.Contract;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.cqrs4j.jsonb.TestUtils.jsonb;
import static org.fuin.utils4j.Utils4J.deserialize;
import static org.fuin.utils4j.Utils4J.serialize;

//CHECKSTYLE:OFF Test code
public class AbstractAggregateCommandTest {

    private static final EventType MY_EVENT_TYPE = new EventType("MyEvent");

    private static final EventType MY_COMMAND_TYPE = new EventType("MyCommandt");

    @Test
    public final void testConstructor() {

        // PREPARE
        final AId aid = new AId(123L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid);
        final AggregateVersion version = new AggregateVersion(1);

        // TEST
        final AbstractAggregateCommand<AId, AId> testee = new MyCommand(entityIdPath, version);

        // VERIFY
        assertThat(testee.getAggregateRootId()).isEqualTo(aid);
        assertThat(testee.getEntityId()).isEqualTo(aid);
        assertThat(testee.getAggregateVersion()).isEqualTo(version);
        assertThat(testee.getEventId()).isNotNull();
        assertThat(testee.getEventTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isNull();
        assertThat(testee.getCorrelationId()).isNull();
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testConstructor2() {

        // PREPARE
        final AId aid = new AId(123L);
        final BId bid = new BId(1L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid, bid);
        final AggregateVersion version = new AggregateVersion(1);

        // TEST
        final AbstractAggregateCommand<AId, BId> testee = new MyCommand2(entityIdPath, version);

        // VERIFY
        assertThat(testee.getAggregateRootId()).isEqualTo(aid);
        assertThat(testee.getEntityId()).isEqualTo(bid);
        assertThat(testee.getAggregateVersion()).isEqualTo(version);
        assertThat(testee.getEventId()).isNotNull();
        assertThat(testee.getEventTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isNull();
        assertThat(testee.getCorrelationId()).isNull();
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testConstructor3() {

        // PREPARE
        final AId aid = new AId(123L);
        final BId bid = new BId(1L);
        final CId cid = new CId(2L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid, bid, cid);
        final AggregateVersion version = new AggregateVersion(1);

        // TEST
        final AbstractAggregateCommand<AId, CId> testee = new MyCommand3(entityIdPath, version);

        // VERIFY
        assertThat(testee.getAggregateRootId()).isEqualTo(aid);
        assertThat(testee.getEntityId()).isEqualTo(cid);
        assertThat(testee.getAggregateVersion()).isEqualTo(version);
        assertThat(testee.getEventId()).isNotNull();
        assertThat(testee.getEventTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isNull();
        assertThat(testee.getCorrelationId()).isNull();
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testConstructorEvent() {

        // PREPARE
        final AId aid = new AId(123L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid);
        final AggregateVersion version = new AggregateVersion(1);
        final EventId correlationId = new EventId();
        final EventId causationId = new EventId();
        final MyEvent event = new MyEvent(correlationId, causationId);

        // TEST
        final AbstractAggregateCommand<?, ?> testee = new MyCommand(entityIdPath, version, event);

        // VERIFY
        assertThat(testee.getAggregateRootId()).isEqualTo(aid);
        assertThat(testee.getEntityId()).isEqualTo(aid);
        assertThat(testee.getAggregateVersion()).isEqualTo(version);
        assertThat(testee.getEventId()).isNotNull();
        assertThat(testee.getEventTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isEqualTo(event.getEventId());
        assertThat(testee.getCorrelationId()).isEqualTo(correlationId);
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testConstructorCorrelationCausation() {

        // PREPARE
        final AId aid = new AId(123L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid);
        final AggregateVersion version = new AggregateVersion(1);
        final EventId correlationId = new EventId();
        final EventId causationId = new EventId();

        // TEST
        final AbstractAggregateCommand<?, ?> testee = new MyCommand(entityIdPath, version, correlationId, causationId);

        // VERIFY
        assertThat(testee.getAggregateRootId()).isEqualTo(aid);
        assertThat(testee.getEntityId()).isEqualTo(aid);
        assertThat(testee.getAggregateVersion()).isEqualTo(version);
        assertThat(testee.getEventId()).isNotNull();
        assertThat(testee.getEventTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isEqualTo(causationId);
        assertThat(testee.getCorrelationId()).isEqualTo(correlationId);
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testBuilder() {

        // PREPARE
        final EventId eventId = new EventId();
        final ZonedDateTime timestamp = ZonedDateTime.now();
        final AId aid = new AId(123L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid);
        final AggregateVersion version = new AggregateVersion(1);
        final EventId correlationId = new EventId();
        final EventId causationId = new EventId();
        final MyCommand.Builder testee = new MyCommand.Builder();

        // TEST
        final MyCommand cmd = testee
                .eventId(eventId)
                .timestamp(timestamp)
                .entityIdPath(entityIdPath)
                .aggregateVersion(version)
                .correlationId(correlationId)
                .causationId(causationId).build();

        // VERIFY
        assertThat(cmd.getEventId()).isEqualTo(eventId);
        assertThat(cmd.getEventTimestamp()).isEqualTo(timestamp);
        assertThat(cmd.getAggregateRootId()).isEqualTo(aid);
        assertThat(cmd.getEntityId()).isEqualTo(aid);
        assertThat(cmd.getAggregateVersion()).isEqualTo(version);
        assertThat(cmd.getEventId()).isNotNull();
        assertThat(cmd.getEventTimestamp()).isNotNull();
        assertThat(cmd.getCausationId()).isEqualTo(causationId);
        assertThat(cmd.getCorrelationId()).isEqualTo(correlationId);
        assertThat(cmd.getEventType()).isEqualTo(MY_COMMAND_TYPE);

    }

    @Test
    public final void testSerializeDeserialize() {

        // PREPARE
        final AId aid = new AId(123L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid);
        final AggregateVersion version = new AggregateVersion(1);
        final EventId correlationId = new EventId();
        final EventId causationId = new EventId();
        final MyCommand original = new MyCommand(entityIdPath, version, correlationId, causationId);

        // TEST
        final MyCommand copy = deserialize(serialize(original));

        // VERIFY
        assertThat(copy).isEqualTo(original);
        assertThat(copy.getEntityIdPath()).isEqualTo(new EntityIdPath(aid));
        assertThat(copy.getAggregateVersion()).isEqualTo(version);
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
            final AId aid = new AId(123L);
            final EntityIdPath entityIdPath = new EntityIdPath(aid);
            final AggregateVersion version = new AggregateVersion(1);
            final EventId correlationId = new EventId();
            final EventId causationId = new EventId();
            final MyCommand original = new MyCommand(entityIdPath, version, correlationId, causationId);

            // TEST
            final String json = jsonb.toJson(original);
            final MyCommand copy = jsonb.fromJson(json, MyCommand.class);

            // VERIFY
            assertThat(copy).isEqualTo(original);
            assertThat(copy.getEntityIdPath()).isEqualTo(new EntityIdPath(aid));
            assertThat(copy.getAggregateVersion()).isEqualTo(version);
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
            final String json = """                    
                    {
                      "entity-id-path" : "A 1/B 2/C 3",
                      "aggregate-version" : 1,
                      "event-id" : "f910c6d7-debc-46e1-ae02-9ca6f4658cf5",
                      "event-timestamp" : "2016-09-18T10:38:08.0+02:00[Europe/Berlin]",
                      "correlation-id" : "2a5893a9-00da-4003-b280-98324eccdef1",
                      "causation-id" : "f13d3481-51b7-423f-8fe7-5c342f7d7c46"
                    }
                    """;

            // TEST
            final MyCommand copy = jsonb.fromJson(json, MyCommand.class);

            // VERIFY
            Contract.requireValid(Validation.buildDefaultValidatorFactory().getValidator(), copy);
            assertThat(copy.getEntityIdPath()).isEqualTo(new EntityIdPath(new AId(1L), new BId(2L), new CId(3L)));
            assertThat(copy.getAggregateVersion()).isEqualTo(new AggregateVersion(1));
            assertThat(copy.getCausationId()).isEqualTo(new EventId(UUID.fromString("f13d3481-51b7-423f-8fe7-5c342f7d7c46")));
            assertThat(copy.getCorrelationId()).isEqualTo(new EventId(UUID.fromString("2a5893a9-00da-4003-b280-98324eccdef1")));
            assertThat(copy.getEventId()).isEqualTo(new EventId(UUID.fromString("f910c6d7-debc-46e1-ae02-9ca6f4658cf5")));
            assertThat(copy.getEventType()).isEqualTo(copy.getEventType());
            assertThat(copy.getEventTimestamp()).isEqualTo(ZonedDateTime.of(2016, 9, 18, 10, 38, 8, 0, ZoneId.of("Europe/Berlin")));

        }

    }

    @Test
    public final void testUnmarshalNullVersion() throws Exception {

        try (final Jsonb jsonb = jsonb()) {

            // PREPARE
            final String json = """
                    {
                      "entity-id-path" : "A 1/B 2/C 3",
                      "event-id" : "f910c6d7-debc-46e1-ae02-9ca6f4658cf5",
                      "event-timestamp" : "2016-09-18T10:38:08.0+02:00[Europe/Berlin]",
                      "correlation-id" : "2a5893a9-00da-4003-b280-98324eccdef1",
                      "causation-id" : "f13d3481-51b7-423f-8fe7-5c342f7d7c46"
                    }
                    """;

            // TEST
            final MyCommand copy = jsonb.fromJson(json, MyCommand.class);

            // VERIFY
            Contract.requireValid(Validation.buildDefaultValidatorFactory().getValidator(), copy);
            assertThat(copy.getEntityIdPath()).isEqualTo(new EntityIdPath(new AId(1L), new BId(2L), new CId(3L)));
            assertThat(copy.getAggregateVersion()).isNull();
            assertThat(copy.getCausationId()).isEqualTo(new EventId(UUID.fromString("f13d3481-51b7-423f-8fe7-5c342f7d7c46")));
            assertThat(copy.getCorrelationId()).isEqualTo(new EventId(UUID.fromString("2a5893a9-00da-4003-b280-98324eccdef1")));
            assertThat(copy.getEventId()).isEqualTo(new EventId(UUID.fromString("f910c6d7-debc-46e1-ae02-9ca6f4658cf5")));
            assertThat(copy.getEventType()).isEqualTo(copy.getEventType());
            assertThat(copy.getEventTimestamp()).isEqualTo(ZonedDateTime.of(2016, 9, 18, 10, 38, 8, 0, ZoneId.of("Europe/Berlin")));

        }

    }

    public static class MyCommand extends AbstractAggregateCommand<AId, AId> {

        private static final long serialVersionUID = 1L;

        public MyCommand() {
            super();
        }

        public MyCommand(EntityIdPath entityIdPath, AggregateVersion aggregateVersion) {
            super(entityIdPath, aggregateVersion);
        }

        public MyCommand(EntityIdPath entityIdPath, AggregateVersion aggregateVersion, Event respondTo) {
            super(entityIdPath, aggregateVersion, respondTo);
        }

        public MyCommand(EntityIdPath entityIdPath, AggregateVersion aggregateVersion, EventId correlationId, EventId causationId) {
            super(entityIdPath, aggregateVersion, correlationId, causationId);
        }

        @Override
        public EventType getEventType() {
            return MY_COMMAND_TYPE;
        }

        public static class Builder extends AbstractAggregateCommand.Builder<AId, AId, MyCommand, Builder> {

            private MyCommand delegate;

            public Builder() {
                super(new MyCommand());
                delegate = delegate();
            }

            public MyCommand build() {
                ensureBuildableAbstractAggregateCommand();
                final MyCommand result = delegate;
                delegate = new MyCommand();
                resetAbstractAggregateCommand(delegate);
                return result;
            }

        }

    }

    public static class MyCommand2 extends AbstractAggregateCommand<AId, BId> {

        private static final long serialVersionUID = 1L;

        public MyCommand2() {
            super();
        }

        public MyCommand2(EntityIdPath entityIdPath, AggregateVersion aggregateVersion) {
            super(entityIdPath, aggregateVersion);
        }

        public MyCommand2(EntityIdPath entityIdPath, AggregateVersion aggregateVersion, Event respondTo) {
            super(entityIdPath, aggregateVersion, respondTo);
        }

        public MyCommand2(EntityIdPath entityIdPath, AggregateVersion aggregateVersion, EventId correlationId, EventId causationId) {
            super(entityIdPath, aggregateVersion, correlationId, causationId);
        }

        @Override
        public EventType getEventType() {
            return MY_COMMAND_TYPE;
        }

    }

    public static class MyCommand3 extends AbstractAggregateCommand<AId, CId> {

        private static final long serialVersionUID = 1L;

        public MyCommand3() {
            super();
        }

        public MyCommand3(EntityIdPath entityIdPath, AggregateVersion aggregateVersion) {
            super(entityIdPath, aggregateVersion);
        }

        public MyCommand3(EntityIdPath entityIdPath, AggregateVersion aggregateVersion, Event respondTo) {
            super(entityIdPath, aggregateVersion, respondTo);
        }

        public MyCommand3(EntityIdPath entityIdPath, AggregateVersion aggregateVersion, EventId correlationId, EventId causationId) {
            super(entityIdPath, aggregateVersion, correlationId, causationId);
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
