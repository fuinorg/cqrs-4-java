/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.ddd4j.jackson.AggregateNotFoundExceptionData;
import org.fuin.objects4j.common.AsStringCapable;
import org.fuin.objects4j.jackson.ValueObjectStringJacksonDeserializer;
import org.fuin.objects4j.jackson.ValueObjectStringJacksonSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link DataResultJacksonDeserializer} class.
 */
public class DataResultJacksonDeserializerTest {

    @Test
    public final void testToFromJson() throws Exception {

        // PREPARE
        final ObjectMapper objectMapper = createObjectMapper();
        final DataResult<MyData> original = DataResult.ok(new MyData(1, "one"), "my-data");

        // TEST
        final String json = objectMapper.writeValueAsString(original);
        final DataResult<MyData> copy = objectMapper.readValue(json, DataResult.class);

        // VERIFY
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonVoidResult() throws IOException {

        // PREPARE
        final ObjectMapper objectMapper = createObjectMapper();
        final String jsonOriginal = """
                {
                    "type": "OK"
                }
                """;

        // TEST
        final DataResult<Void> original = objectMapper.readValue(jsonOriginal, DataResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.OK);
        assertThat(original.getCode()).isNull();
        assertThat(original.getMessage()).isNull();
        assertThat(original.getData()).isNull();
        assertThat(original.getDataClass()).isNull();
        assertThat(original.getDataElement()).isNull();

        // TEST
        final String jsonCopy = objectMapper.writeValueAsString(original);
        final DataResult<Void> copy = objectMapper.readValue(jsonCopy, DataResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonSimpleResultOK() throws IOException {

        // PREPARE
        final ObjectMapper objectMapper = createObjectMapper();
        final String jsonOriginal = """
                {
                    "type": "OK"
                }
                """;

        // TEST
        final SimpleResult original = objectMapper.readValue(jsonOriginal, SimpleResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.OK);
        assertThat(original.getCode()).isNull();
        assertThat(original.getMessage()).isNull();
        assertThat(original.getData()).isNull();

        // TEST
        final String jsonCopy = objectMapper.writeValueAsString(original);
        final SimpleResult copy = objectMapper.readValue(jsonCopy, SimpleResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonSimpleResultException() throws IOException {

        // PREPARE
        final ObjectMapper objectMapper = createObjectMapper();
        final String jsonOriginal = """
                {
                    "type": "ERROR",
                    "code": "DDD4J-AGGREGATE_NOT_FOUND",
                    "message": "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found"
                }
                """;

        // TEST
        final SimpleResult original = objectMapper.readValue(jsonOriginal, SimpleResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.ERROR);
        assertThat(original.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(original.getMessage()).isEqualTo("Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found");
        assertThat(original.getData()).isNull();

        // TEST
        final String jsonCopy = objectMapper.writeValueAsString(original);
        final SimpleResult copy = objectMapper.readValue(jsonCopy, SimpleResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonResultData() throws IOException {

        // PREPARE
        final ObjectMapper objectMapper = createObjectMapper();
        final String jsonOriginal = """
                {
                	"type": "OK",
                	"data-class": "org.fuin.cqrs4j.jackson.Invoice",
                	"data-element": "invoice",
                	"invoice": {
                		"id" : "I-0123456"
                	}
                }
                """;

        // TEST
        final DataResult<Invoice> original = objectMapper.readValue(jsonOriginal, DataResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.OK);
        assertThat(original.getCode()).isNull();
        assertThat(original.getMessage()).isNull();
        assertThat(original.getDataClass()).isEqualTo(Invoice.class.getName());
        assertThat(original.getDataElement()).isEqualTo("invoice");
        assertThat(original.getData()).isInstanceOf(Invoice.class);
        assertThat(original.getData().getId()).isEqualTo("I-0123456");

        // TEST
        final String jsonCopy = objectMapper.writeValueAsString(original);
        final DataResult<Invoice> copy = objectMapper.readValue(jsonCopy, DataResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonResultException() throws IOException {

        // PREPARE
        final ObjectMapper objectMapper = createObjectMapper();
        final String jsonOriginal = """
                {
                	"type": "ERROR",
                	"code": "DDD4J-AGGREGATE_NOT_FOUND",
                	"message": "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found",
                	"data-class": "org.fuin.ddd4j.jackson.AggregateNotFoundExceptionData",
                	"data-element": "aggregate-not-found-exception",
                	"aggregate-not-found-exception": {
                		"msg": "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found",
                		"sid": "DDD4J-AGGREGATE_NOT_FOUND",
                		"aggregate-type": "Invoice",
                		"aggregate-id": "4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119"
                	}
                }
                """;

        // TEST
        final DataResult<AggregateNotFoundExceptionData> original = objectMapper.readValue(jsonOriginal, DataResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.ERROR);
        assertThat(original.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(original.getMessage()).isEqualTo("Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found");
        assertThat(original.getDataClass()).isEqualTo("org.fuin.ddd4j.jackson.AggregateNotFoundExceptionData");
        assertThat(original.getDataElement()).isEqualTo("aggregate-not-found-exception");
        assertThat(original.getData()).isInstanceOf(AggregateNotFoundExceptionData.class);

        // TEST
        final String jsonCopy = objectMapper.writeValueAsString(original);
        final DataResult<AggregateNotFoundExceptionData> copy = objectMapper.readValue(jsonCopy, DataResult.class);
        assertThat(copy).isEqualTo(original);

    }

    public static ObjectMapper createObjectMapper() {
        return TestUtils.objectMapper()
                .registerModule(new TestAdapterModule());
    }


    public static final class InvoiceId implements AsStringCapable {

        private String id;

        public InvoiceId(final String id) {
            super();
            this.id = id;
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
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            InvoiceId other = (InvoiceId) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return id;
        }

        @Override
        public String asString() {
            return id;
        }

    }

    public static class TestAdapterModule extends Module {

        @Override
        public String getModuleName() {
            return "TestModule";
        }

        @Override
        public void setupModule(SetupContext context) {

            final SimpleSerializers serializers = new SimpleSerializers();
            serializers.addSerializer(new ValueObjectStringJacksonSerializer<>(InvoiceId.class));
            context.addSerializers(serializers);

            final SimpleDeserializers deserializers = new SimpleDeserializers();
            deserializers.addDeserializer(InvoiceId.class, new ValueObjectStringJacksonDeserializer<>(InvoiceId.class, InvoiceId::new));
            context.addDeserializers(deserializers);
        }

        @Override
        public Version version() {
            return new Version(1, 0, 0, null,
                    "foo", "bar");
        }

    }

    public static final class MyData {

        @JsonProperty("id")
        private int id;

        @JsonProperty("name")
        private String name;

        protected MyData() {
            super();
        }

        public MyData(final int id, final String name) {
            super();
            this.id = id;
            this.name = name;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + id;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MyData other = (MyData) obj;
            if (id != other.id)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "MyData [id=" + id + ", name=" + name + "]";
        }

    }

}
