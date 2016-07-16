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
import static org.fuin.utils4j.Utils4J.deserialize;
import static org.fuin.utils4j.Utils4J.serialize;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.fuin.ddd4j.ddd.AggregateNotFoundException;
import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.ddd4j.ddd.EntityType;
import org.fuin.ddd4j.ddd.StringBasedEntityType;
import org.junit.Test;
import org.w3c.dom.Node;

// CHECKSTYLE:OFF
public final class CommandResultTest {

    private static final EntityType TEST_TYPE = new StringBasedEntityType("Test");

    @Test
    public final void testConstructorAll() {

        // PREPARE
        final String data = "Whatever";
        final ZonedDateTime requestCreated = ZonedDateTime.now();

        // TEST
        final CommandResult testee = new CommandResult(ResultType.OK, "0", "Yes!", requestCreated, data);

        // VERIFY
        assertThat(testee.getType()).isEqualTo(ResultType.OK);
        assertThat(testee.getCode()).isEqualTo("0");
        assertThat(testee.getMessage()).isEqualTo("Yes!");
        assertThat(testee.getRequestCreated()).isEqualTo(requestCreated);
        assertThat(testee.getResponseCreated()).isNotNull();
        assertThat(testee.getData()).isEqualTo(data);

    }

    @Test
    public final void testConstructorException() {

        // PREPARE
        final TestId id = new TestId();
        final AggregateNotFoundException ex = new AggregateNotFoundException(TEST_TYPE, id);
        System.out.println("MESSAGE=" + ex.getMessage());
        final ZonedDateTime requestCreated = ZonedDateTime.now();

        // TEST
        final CommandResult testee = new CommandResult(ResultType.APPLICATION_ERROR, ex, requestCreated);

        // VERIFY
        assertThat(testee.getType()).isEqualTo(ResultType.APPLICATION_ERROR);
        assertThat(testee.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(testee.getMessage()).isEqualTo(TEST_TYPE + " with id " + id.asString() + " not found");
        assertThat(testee.getRequestCreated()).isEqualTo(requestCreated);
        assertThat(testee.getResponseCreated()).isNotNull();
        assertThat(testee.getData()).isEqualTo(ex);

    }

    @Test
    public final void testSerializeDeserialize() {

        // PREPARE
        final CommandResult original = CommandResult.ok("Yes!", ZonedDateTime.now());

        // TEST
        final CommandResult copy = deserialize(serialize(original));

        // VERIFY
        assertThat(original).isEqualTo(copy);

    }

    @Test
    public final void testMarshalUnmarshal() {

        // PREPARE
        final CommandResult original = CommandResult.ok("Yes!", ZonedDateTime.now());

        // TEST
        final String xml = marshal(original, CommandResult.class);
        final CommandResult copy = unmarshal(xml, CommandResult.class);

        // VERIFY
        assertThat(original).isEqualTo(copy);

    }

    @Test
    public final void testUnmarshalVoidResult() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(this.getClass().getResourceAsStream("/command-result-void.xml"),
                "utf-8");

        // TEST
        final CommandResult copy = unmarshal(xml, CommandResult.class);

        // VERIFY
        assertThat(copy.getCode()).isEqualTo("0");
        assertThat(copy.getType()).isEqualTo(ResultType.OK);
        assertThat(copy.getMessage()).isEqualTo("Customer successfully created");
        assertThat(copy.getRequestCreated()).isNotNull();
        assertThat(copy.getResponseCreated()).isNotNull();

    }

    @Test
    public final void testUnmarshalDataResult() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(this.getClass().getResourceAsStream("/command-result-data.xml"),
                "utf-8");

        // TEST
        final CommandResult copy = unmarshal(xml, CommandResult.class);

        // VERIFY
        assertThat(copy.getCode()).isEqualTo("0");
        assertThat(copy.getType()).isEqualTo(ResultType.OK);
        assertThat(copy.getMessage()).isEqualTo("Invoice successfully created");
        assertThat(copy.getRequestCreated()).isNotNull();
        assertThat(copy.getResponseCreated()).isNotNull();
        assertThat(copy.getData()).isInstanceOf(Node.class);
        final Node node = (Node) copy.getData();
        assertThat(node.getNodeName()).isEqualTo("invoice-id");
        assertThat(node.getTextContent()).isEqualTo("I-0123456");

    }

    @Test
    public final void testUnmarshalExceptionResult() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(
                this.getClass().getResourceAsStream("/command-result-exception.xml"), "utf-8");

        // TEST
        final CommandResult copy = unmarshal(xml, CommandResult.class, AggregateNotFoundException.class);

        // VERIFY
        final String msg = "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found";
        assertThat(copy.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(copy.getType()).isEqualTo(ResultType.APPLICATION_ERROR);
        assertThat(copy.getMessage()).isEqualTo(msg);
        assertThat(copy.getRequestCreated()).isNotNull();
        assertThat(copy.getResponseCreated()).isNotNull();
        assertThat(copy.getData()).isInstanceOf(AggregateNotFoundException.class);
        final AggregateNotFoundException ex = (AggregateNotFoundException) copy.getData();
        assertThat(ex.getMessage()).isEqualTo(msg);
        assertThat(ex.getAggregateType()).isEqualTo("Invoice");
        assertThat(ex.getAggregateId()).isEqualTo("4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119");

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

}
// CHECKSTYLE:ON
