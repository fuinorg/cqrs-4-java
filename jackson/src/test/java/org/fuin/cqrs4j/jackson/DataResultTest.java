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

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.core.StringBasedEntityType;
import org.fuin.ddd4j.jackson.AggregateNotFoundExceptionData;
import org.junit.jupiter.api.Test;

import java.io.Serial;
import java.util.UUID;

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

public final class DataResultTest {

    private static final EntityType TEST_TYPE = new StringBasedEntityType("Test");

    @Test
    public final void testEqualsHashCode() {
        EqualsVerifier.simple().forClass(DataResult.class).verify();
    }

    @Test
    public final void testConstructorAll() {

        // PREPARE
        final String data = "Whatever";

        // TEST
        final DataResult<String> testee = new DataResult<>(ResultType.WARNING, "X1", "Yes!", data);

        // VERIFY
        assertThat(testee.getType()).isEqualTo(ResultType.WARNING);
        assertThat(testee.getCode()).isEqualTo("X1");
        assertThat(testee.getMessage()).isEqualTo("Yes!");
        assertThat(testee.getData()).isEqualTo(data);

    }

    @Test
    public final void testConstructorException() {

        // PREPARE
        final TestId id = new TestId();
        final AggregateNotFoundException ex = new AggregateNotFoundException(TEST_TYPE, id);
        final AggregateNotFoundExceptionData exData = new AggregateNotFoundExceptionData(ex);

        // TEST
        final DataResult<AggregateNotFoundExceptionData> testee = new DataResult<>(exData);

        // VERIFY
        assertThat(testee.getType()).isEqualTo(ResultType.ERROR);
        assertThat(testee.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(testee.getMessage()).isEqualTo(TEST_TYPE + " with id " + id.asString() + " not found");
        assertThat(testee.getData()).isInstanceOf(AggregateNotFoundExceptionData.class);

    }

    @Test
    public final void testUnmarshalMarshalVoidResult() throws Exception {

        final ObjectMapper objectMapper = TestUtils.objectMapper();

        // PREPARE
        final String originalJson = """
                {
                    "type": "OK"
                }
                """;

        // TEST
        final DataResult<Void> copy = objectMapper.readValue(originalJson, DataResult.class);

        // VERIFY
        assertThat(copy.getType()).isEqualTo(ResultType.OK);

        // TEST
        final String copyJson = objectMapper.writeValueAsString(copy);

        // VERIFY
        assertThatJson(copyJson).isEqualTo(originalJson);

    }

    @Test
    public final void testUnmarshalMarshalDataResult() throws Exception {

        final ObjectMapper objectMapper = TestUtils.objectMapper();

        // PREPARE
        final String originalJson = """
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
        final DataResult<Invoice> copy = objectMapper.readValue(originalJson, DataResult.class);

        // VERIFY
        assertThat(copy.getType()).isEqualTo(ResultType.OK);
        assertThat(copy.getCode()).isNull();
        assertThat(copy.getMessage()).isNull();
        assertThat(copy.getData()).isInstanceOf(Invoice.class);
        assertThat(copy.getData().getId()).isEqualTo("I-0123456");

        // TEST
        final String copyJson = objectMapper.writeValueAsString(copy);

        // VERIFY
        assertThatJson(copyJson).isEqualTo(originalJson);

    }

    @Test
    public final void testUnmarshalExceptionResult() throws Exception {

        final ObjectMapper objectMapper = TestUtils.objectMapper();

        // PREPARE
        final String originalJson = """
                {
                    "type": "ERROR",
                    "code": "DDD4J-AGGREGATE_NOT_FOUND",
                    "message": "Vendor with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found",
                    "data-class": "org.fuin.ddd4j.jackson.AggregateNotFoundExceptionData",
                    "data-element": "aggregate-not-found-exception",
                    "aggregate-not-found-exception" : {
                        "msg" : "Vendor with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found",
                        "sid" : "DDD4J-AGGREGATE_NOT_FOUND",
                        "aggregate-type" : "Vendor",
                        "aggregate-id" : "4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119"
                    }
                }
                """;

        // TEST
        final DataResult<AggregateNotFoundExceptionData> copy = objectMapper.readValue(originalJson, DataResult.class);

        // VERIFY
        final String msg = "Vendor with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found";
        assertThat(copy.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(copy.getType()).isEqualTo(ResultType.ERROR);
        assertThat(copy.getMessage()).isEqualTo(msg);
        assertThat(copy.getData()).isInstanceOf(AggregateNotFoundExceptionData.class);
        final AggregateNotFoundException anfe = copy.getData().toException();
        assertThat(anfe.getMessage()).isEqualTo(msg);
        assertThat(anfe.getType()).isEqualTo("Vendor");
        assertThat(anfe.getId()).isEqualTo("4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119");

        // TEST
        final String copyJson = objectMapper.writeValueAsString(copy);

        // VERIFY
        assertThatJson(copyJson).isEqualTo(originalJson);

    }

    private static class TestId implements AggregateRootId {

        @Serial
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

}
