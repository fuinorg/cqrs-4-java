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
package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbProperty;
import org.eclipse.yasson.FieldAccessStrategy;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.ddd4j.jsonb.AggregateNotFoundExceptionData;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link DataResultJsonbAdapter} class.
 */
public class DataResultJsonbAdapterTest {

    @Test
    public final void testToFromJson() throws Exception {

        // PREPARE
        final Jsonb jsonb = jsonbb();
        final DataResult<MyData> original = DataResult.ok(new MyData(1, "one"), "my-data");

        // TEST
        final String json = jsonb.toJson(original);
        final DataResult<MyData> copy = jsonb.fromJson(json, DataResult.class);

        // VERIFY
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonVoidResult() throws IOException {

        // PREPARE
        final Jsonb jsonb = jsonbb();
        final String jsonOriginal = """
                {
                    "type": "OK"
                }
                """;

        // TEST
        final DataResult<Void> original = jsonb.fromJson(jsonOriginal, DataResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.OK);
        assertThat(original.getCode()).isNull();
        assertThat(original.getMessage()).isNull();
        assertThat(original.getData()).isNull();
        assertThat(original.getDataClass()).isNull();
        assertThat(original.getDataElement()).isNull();

        // TEST
        final String jsonCopy = jsonb.toJson(original);
        final DataResult<Void> copy = jsonb.fromJson(jsonCopy, DataResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonSimpleResultOK() throws IOException {

        // PREPARE
        final Jsonb jsonb = jsonbb();
        final String jsonOriginal = """
                {
                    "type": "OK"
                }
                """;

        // TEST
        final SimpleResult original = jsonb.fromJson(jsonOriginal, SimpleResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.OK);
        assertThat(original.getCode()).isNull();
        assertThat(original.getMessage()).isNull();
        assertThat(original.getData()).isNull();

        // TEST
        final String jsonCopy = jsonb.toJson(original);
        final SimpleResult copy = jsonb.fromJson(jsonCopy, SimpleResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonSimpleResultException() throws IOException {

        // PREPARE
        final Jsonb jsonb = jsonbb();
        final String jsonOriginal = """
                {
                    "type": "ERROR",
                    "code": "DDD4J-AGGREGATE_NOT_FOUND",
                    "message": "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found"
                }
                """;

        // TEST
        final SimpleResult original = jsonb.fromJson(jsonOriginal, SimpleResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.ERROR);
        assertThat(original.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(original.getMessage()).isEqualTo("Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found");
        assertThat(original.getData()).isNull();

        // TEST
        final String jsonCopy = jsonb.toJson(original);
        final SimpleResult copy = jsonb.fromJson(jsonCopy, SimpleResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonResultData() throws IOException {

        // PREPARE
        final Jsonb jsonb = jsonbb();
        final String jsonOriginal = """
                {
                	"type": "OK",
                	"data-class": "org.fuin.cqrs4j.jsonb.DataResultTest$Invoice",
                	"data-element": "invoice",
                	"invoice": {
                		"id" : "I-0123456"
                	}
                }
                """;

        // TEST
        final DataResult<DataResultTest.Invoice> original = jsonb.fromJson(jsonOriginal, DataResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.OK);
        assertThat(original.getCode()).isNull();
        assertThat(original.getMessage()).isNull();
        assertThat(original.getDataClass()).isEqualTo(DataResultTest.Invoice.class.getName());
        assertThat(original.getDataElement()).isEqualTo("invoice");
        assertThat(original.getData()).isInstanceOf(DataResultTest.Invoice.class);
        assertThat(((DataResultTest.Invoice) original.getData()).getId()).isEqualTo("I-0123456");

        // TEST
        final String jsonCopy = jsonb.toJson(original);
        final DataResult<DataResultTest.Invoice> copy = jsonb.fromJson(jsonCopy, DataResult.class);
        assertThat(copy).isEqualTo(original);

    }

    @Test
    public final void testFromToJsonResultException() throws IOException {

        // PREPARE
        final Jsonb jsonb = jsonbb();
        final String jsonOriginal = """
                {
                	"type": "ERROR",
                	"code": "DDD4J-AGGREGATE_NOT_FOUND",
                	"message": "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found",
                	"data-class": "org.fuin.ddd4j.jsonb.AggregateNotFoundExceptionData",
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
        final DataResult<AggregateNotFoundExceptionData> original = jsonb.fromJson(jsonOriginal, DataResult.class);

        // VERIFY
        assertThat(original.getType()).isEqualTo(ResultType.ERROR);
        assertThat(original.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(original.getMessage()).isEqualTo("Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found");
        assertThat(original.getDataClass()).isEqualTo("org.fuin.ddd4j.jsonb.AggregateNotFoundExceptionData");
        assertThat(original.getDataElement()).isEqualTo("aggregate-not-found-exception");
        assertThat(original.getData()).isInstanceOf(AggregateNotFoundExceptionData.class);

        // TEST
        final String jsonCopy = jsonb.toJson(original);
        final DataResult<AggregateNotFoundExceptionData> copy = jsonb.fromJson(jsonCopy, DataResult.class);
        assertThat(copy).isEqualTo(original);

    }

    private static Jsonb jsonb() {
        final JsonbConfig config = new JsonbConfig().withPropertyVisibilityStrategy(new FieldAccessStrategy())
                .withAdapters(new InvoiceIdAdapter());
        return JsonbBuilder.create(config);
    }

    private static Jsonb jsonb(final Jsonb jsonb) {
        final JsonbConfig config = new JsonbConfig().withPropertyVisibilityStrategy(new FieldAccessStrategy())
                .withAdapters(new DataResultJsonbAdapter(jsonb), new InvoiceIdAdapter());
        return JsonbBuilder.create(config);
    }

    private static Jsonb jsonbb() {
        return jsonb(jsonb());
    }

    public static final class InvoiceId {

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

    }

    /**
     * Converts the value object from/to string.
     */
    public static final class InvoiceIdAdapter implements JsonbAdapter<InvoiceId, String> {

        @Override
        public final String adaptToJson(final InvoiceId value) throws Exception {
            if (value == null) {
                return null;
            }
            return value.toString();
        }

        @Override
        public final InvoiceId adaptFromJson(final String value) throws Exception {
            if (value == null) {
                return null;
            }
            return new InvoiceId(value);
        }

    }

    public static final class MyData {

        @JsonbProperty("id")
        private int id;

        @JsonbProperty("name")
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
