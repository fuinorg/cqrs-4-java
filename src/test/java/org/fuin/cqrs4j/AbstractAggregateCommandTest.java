package org.fuin.cqrs4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.utils4j.JaxbUtils.marshal;
import static org.fuin.utils4j.JaxbUtils.unmarshal;
import static org.fuin.utils4j.Utils4J.deserialize;
import static org.fuin.utils4j.Utils4J.serialize;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.validation.Validation;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.fuin.ddd4j.ddd.AbstractEvent;
import org.fuin.ddd4j.ddd.AggregateVersion;
import org.fuin.ddd4j.ddd.EntityId;
import org.fuin.ddd4j.ddd.EntityIdFactory;
import org.fuin.ddd4j.ddd.EntityIdPath;
import org.fuin.ddd4j.ddd.EntityIdPathConverter;
import org.fuin.ddd4j.ddd.Event;
import org.fuin.ddd4j.ddd.EventId;
import org.fuin.ddd4j.ddd.EventType;
import org.junit.Test;

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
        assertThat(testee.getTimestamp()).isNotNull();
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
        assertThat(testee.getTimestamp()).isNotNull();
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
        assertThat(testee.getTimestamp()).isNotNull();
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
        assertThat(testee.getTimestamp()).isNotNull();
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
        assertThat(testee.getTimestamp()).isNotNull();
        assertThat(testee.getCausationId()).isEqualTo(causationId);
        assertThat(testee.getCorrelationId()).isEqualTo(correlationId);
        assertThat(testee.getEventType()).isEqualTo(MY_COMMAND_TYPE);

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
        assertThat(copy.getTimestamp()).isEqualTo(original.getTimestamp());

    }

    @Test
    public final void testMarshalUnmarshal() {

        // PREPARE
        final AId aid = new AId(123L);
        final EntityIdPath entityIdPath = new EntityIdPath(aid);
        final AggregateVersion version = new AggregateVersion(1);
        final EventId correlationId = new EventId();
        final EventId causationId = new EventId();
        final MyCommand original = new MyCommand(entityIdPath, version, correlationId, causationId);

        // TEST
        final String xml = marshal(original, createXmlAdapter(), MyCommand.class);
        final MyCommand copy = unmarshal(xml, createXmlAdapter(), MyCommand.class);

        // VERIFY
        assertThat(copy).isEqualTo(original);
        assertThat(copy.getEntityIdPath()).isEqualTo(new EntityIdPath(aid));
        assertThat(copy.getAggregateVersion()).isEqualTo(version);
        assertThat(copy.getCausationId()).isEqualTo(original.getCausationId());
        assertThat(copy.getCorrelationId()).isEqualTo(original.getCorrelationId());
        assertThat(copy.getEventId()).isEqualTo(original.getEventId());
        assertThat(copy.getEventType()).isEqualTo(original.getEventType());
        assertThat(copy.getTimestamp()).isEqualTo(original.getTimestamp());

    }

    @Test
    public final void testUnmarshal() {

        // PREPARE
        final String xml = "<my-command><entity-id-path>A 1/B 2/C 3</entity-id-path>" + "<aggregate-version>1</aggregate-version>"
                + "<event-id>f910c6d7-debc-46e1-ae02-9ca6f4658cf5</event-id>"
                + "<event-timestamp>2016-09-18T10:38:08.0+02:00[Europe/Berlin]</event-timestamp>"
                + "<correlation-id>2a5893a9-00da-4003-b280-98324eccdef1</correlation-id>"
                + "<causation-id>f13d3481-51b7-423f-8fe7-5c342f7d7c46</causation-id></my-command>";

        // TEST
        final MyCommand copy = unmarshal(xml, createXmlAdapter(), MyCommand.class);

        // VERIFY
        Cqrs4JUtils.verifyPrecondition(Validation.buildDefaultValidatorFactory().getValidator(), copy);
        assertThat(copy.getEntityIdPath()).isEqualTo(new EntityIdPath(new AId(1L), new BId(2L), new CId(3L)));
        assertThat(copy.getAggregateVersion()).isEqualTo(new AggregateVersion(1));
        assertThat(copy.getCausationId()).isEqualTo(new EventId(UUID.fromString("f13d3481-51b7-423f-8fe7-5c342f7d7c46")));
        assertThat(copy.getCorrelationId()).isEqualTo(new EventId(UUID.fromString("2a5893a9-00da-4003-b280-98324eccdef1")));
        assertThat(copy.getEventId()).isEqualTo(new EventId(UUID.fromString("f910c6d7-debc-46e1-ae02-9ca6f4658cf5")));
        assertThat(copy.getEventType()).isEqualTo(copy.getEventType());
        assertThat(copy.getTimestamp()).isEqualTo(ZonedDateTime.of(2016, 9, 18, 10, 38, 8, 0, ZoneId.of("Europe/Berlin")));

    }

    @Test
    public final void testUnmarshalNullVersion() {

        // PREPARE
        final String xml = "<my-command><entity-id-path>A 1/B 2/C 3</entity-id-path>"
                + "<event-id>f910c6d7-debc-46e1-ae02-9ca6f4658cf5</event-id>"
                + "<event-timestamp>2016-09-18T10:38:08.0+02:00[Europe/Berlin]</event-timestamp>"
                + "<correlation-id>2a5893a9-00da-4003-b280-98324eccdef1</correlation-id>"
                + "<causation-id>f13d3481-51b7-423f-8fe7-5c342f7d7c46</causation-id></my-command>";

        // TEST
        final MyCommand copy = unmarshal(xml, createXmlAdapter(), MyCommand.class);

        // VERIFY
        Cqrs4JUtils.verifyPrecondition(Validation.buildDefaultValidatorFactory().getValidator(), copy);
        assertThat(copy.getEntityIdPath()).isEqualTo(new EntityIdPath(new AId(1L), new BId(2L), new CId(3L)));
        assertThat(copy.getAggregateVersion()).isNull();
        assertThat(copy.getCausationId()).isEqualTo(new EventId(UUID.fromString("f13d3481-51b7-423f-8fe7-5c342f7d7c46")));
        assertThat(copy.getCorrelationId()).isEqualTo(new EventId(UUID.fromString("2a5893a9-00da-4003-b280-98324eccdef1")));
        assertThat(copy.getEventId()).isEqualTo(new EventId(UUID.fromString("f910c6d7-debc-46e1-ae02-9ca6f4658cf5")));
        assertThat(copy.getEventType()).isEqualTo(copy.getEventType());
        assertThat(copy.getTimestamp()).isEqualTo(ZonedDateTime.of(2016, 9, 18, 10, 38, 8, 0, ZoneId.of("Europe/Berlin")));

    }

    @XmlRootElement(name = "my-command")
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

    }

    @XmlRootElement(name = "my-command-2")
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

    @XmlRootElement(name = "my-command-3")
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

    private static final class MyIdFactory implements EntityIdFactory {
        @Override
        public EntityId createEntityId(final String type, final String id) {
            if (type.equals("A")) {
                return new AId(Long.valueOf(id));
            }
            if (type.equals("B")) {
                return new BId(Long.valueOf(id));
            }
            if (type.equals("C")) {
                return new CId(Long.valueOf(id));
            }
            throw new IllegalArgumentException("Unknown type: '" + type + "'");
        }

        @Override
        public boolean containsType(final String type) {
            if (type.equals("A")) {
                return true;
            }
            if (type.equals("B")) {
                return true;
            }
            if (type.equals("C")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean isValid(String type, String id) {
            return true;
        }
    }

    @SuppressWarnings("rawtypes")
    private static XmlAdapter[] createXmlAdapter() {
        return new XmlAdapter[] { new EntityIdPathConverter(new MyIdFactory()) };
    }

}
// CHECKSTYLE:ON
