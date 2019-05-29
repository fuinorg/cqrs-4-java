/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved. 
 * http://www.fuin.org/
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.utils4j.JaxbUtils.marshal;
import static org.fuin.utils4j.JaxbUtils.unmarshal;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.UUID;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import org.apache.commons.io.IOUtils;
import org.eclipse.yasson.FieldAccessStrategy;
import org.fuin.ddd4j.ddd.AggregateNotFoundException;
import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.ddd4j.ddd.EntityType;
import org.fuin.ddd4j.ddd.StringBasedEntityType;
import org.junit.Test;

// CHECKSTYLE:OFF
public final class DataTest {

    private static final EntityType TEST_TYPE = new StringBasedEntityType("Invoice");

    @Test
    public final void testConstruction() {

        // TEST
        final Data<String> testee = new Data<>("X1", "Whatever");

        // VERIFY
        assertThat(testee.getContentType()).isEqualTo("X1");
        assertThat(testee.getContentData()).isEqualTo("Whatever");

    }

    @Test
    public final void testConstructorException() {

        // PREPARE
        final TestId id = new TestId();
        final AggregateNotFoundException ex = new AggregateNotFoundException(TEST_TYPE, id);

        // TEST
        final Data<AggregateNotFoundException> testee = new Data<>(ex.getShortId(), ex);

        // VERIFY
        assertThat(testee.getContentType()).isEqualTo(ex.getShortId());
        assertThat(testee.getContentData()).isEqualTo(ex);

    }

    @Test
    public final void testMarshalUnmarshalExceptionXml() {

        // PREPARE
        final TestId id = new TestId();
        final AggregateNotFoundException ex = new AggregateNotFoundException(TEST_TYPE, id);
        final Data<AggregateNotFoundException> original = new Data<>(ex.getShortId(), ex);

        // TEST
        final String xml = marshal(original, Data.class, AggregateNotFoundException.class);
        final Data<AggregateNotFoundException> copy = unmarshal(xml, Data.class, AggregateNotFoundException.class);

        assertThat(copy.getContentData()).isInstanceOf(AggregateNotFoundException.class);
        final AggregateNotFoundException anfe = (AggregateNotFoundException) copy.getContentData();
        assertThat(anfe.getMessage()).isEqualTo("Invoice with id " + id.asString() + " not found");
        assertThat(anfe.getAggregateType()).isEqualTo("Invoice");
        assertThat(anfe.getAggregateId()).isEqualTo(id.asString());

    }

    @Test
    public final void testUnmarshalDataExceptionXml() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(this.getClass().getResourceAsStream("/data-exception.xml"), "utf-8");

        // TEST
        final Data<AggregateNotFoundException> copy = unmarshal(xml, Data.class, AggregateNotFoundException.class);

        // VERIFY
        assertThat(copy.getContentType()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(copy.getContentData()).isInstanceOf(AggregateNotFoundException.class);
        final AggregateNotFoundException anfe = (AggregateNotFoundException) copy.getContentData();
        assertThat(anfe.getMessage()).isEqualTo("Invoice with id d89fc328-c53f-4250-bcba-439501d002cf not found");
        assertThat(anfe.getAggregateType()).isEqualTo("Invoice");
        assertThat(anfe.getAggregateId()).isEqualTo("d89fc328-c53f-4250-bcba-439501d002cf");

    }

    @Test
    public final void testMarshalUnmarshalDataXml() {

        // PREPARE
        final Data<MyData> original = new Data<MyData>(MyData.TYPE, new MyData(1));

        // TEST
        final String xml = marshal(original, Data.class, MyData.class);
        final Data<MyData> copy = unmarshal(xml, Data.class, MyData.class);

        // VERIFY
        assertThat(original).isEqualTo(copy);

    }

    @Test
    public final void testUnmarshalDataDataXml() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(this.getClass().getResourceAsStream("/data-data.xml"), "utf-8");

        // TEST
        final Data<MyData> copy = unmarshal(xml, Data.class, MyData.class);

        // VERIFY
        assertThat(copy.getContentType()).isEqualTo(MyData.TYPE);
        assertThat(copy.getContentData()).isInstanceOf(MyData.class);

    }

    public final void testUnmarshalDataDataJson() {

        // PREPARE
        final JsonbConfig config = new JsonbConfig().withAdapters(JSONB_ADAPTERS).withPropertyVisibilityStrategy(new FieldAccessStrategy());
        final Jsonb jsonb = JsonbBuilder.create(config);
        final String json = IOUtils.toString(this.getClass().getResourceAsStream("/data-data.json"), "utf-8");

        // TEST
        final Data<MyData> copy = jsonb.fromJson(json, Data.class);

        // VERIFY
        assertThat(copy.getContentType()).isEqualTo(MyData.TYPE);
        assertThat(copy.getContentData()).isInstanceOf(MyData.class);

    }

    private static class TestId implements AggregateRootId {

        private static final long serialVersionUID = 1L;

        private UUID id = UUID.randomUUID();

        @Override
        public EntityType getType() {
            return TEST_TYPE;
        }

        @Override
        public String asTypedString() {
            return TEST_TYPE + " " + id;
        }

        @Override
        public String asString() {
            return id.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof TestId)) {
                return false;
            }
            TestId other = (TestId) obj;
            if (id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!id.equals(other.id)) {
                return false;
            }
            return true;
        }

    }

    @XmlRootElement(name = "my-data")
    public static final class MyData {

        public static final String TYPE = "my-data";

        @XmlValue
        private int id;

        protected MyData() {
            super();
        }

        public MyData(final int id) {
            super();
            this.id = id;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + id;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof MyData)) {
                return false;
            }
            MyData other = (MyData) obj;
            if (id != other.id) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "" + id;
        }

    }

    public class MyDataSerDeserializer implements JsonbSerializer<MyData>, JsonbDeserializer<MyData> {

        @Override
        public void serialize(final MyData obj, final JsonGenerator generator, final SerializationContext ctx) {
            generator.writeStartObject();
            generator.write("id", obj.id);
            generator.writeEnd();
        }

        @Override
        public MyData deserialize(final JsonParser parser, final DeserializationContext ctx, final Type rtType) {
            final MyData myData = new MyData();
            while (parser.hasNext()) {
                JsonParser.Event event = parser.next();
                if (event == JsonParser.Event.KEY_NAME) {
                    String keyName = parser.getString();
                    if (keyName.equals("id")) {
                        return ctx.deserialize(Integer.class, parser);
                    }
                }
                parser.next();
            }
            return myData;
        }

    }

}
// CHECKSTYLE:ON
