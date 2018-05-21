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
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import org.apache.commons.io.IOUtils;
import org.fuin.ddd4j.ddd.AggregateNotFoundException;
import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.ddd4j.ddd.EntityType;
import org.fuin.ddd4j.ddd.StringBasedEntityType;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

// CHECKSTYLE:OFF
public final class ResultTest {

    private static final EntityType TEST_TYPE = new StringBasedEntityType("Test");

    @Test
    public final void testConstructorAll() {

        // PREPARE
        final String data = "Whatever";

        // TEST
        final Result<String> testee = new Result<>(ResultType.WARNING, "X1", "Yes!", data);

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

        // TEST
        final Result<Void> testee = new Result<>(ex);

        // VERIFY
        assertThat(testee.getType()).isEqualTo(ResultType.ERROR);
        assertThat(testee.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(testee.getMessage()).isEqualTo(TEST_TYPE + " with id " + id.asString() + " not found");
        assertThat(testee.getData()).isNull();
        assertThat(testee.getException()).isInstanceOf(AggregateNotFoundException.class);

    }

    @Test
    public final void testMarshalUnmarshal() {

        // PREPARE
        final Result<Data> original = Result.ok(new Data(1));

        // TEST
        final String xml = marshal(original, Result.class, Data.class);
        final Result<Data> copy = unmarshal(xml, Result.class, Data.class);

        // VERIFY
        assertThat(original).isEqualTo(copy);

    }

    @Test
    public final void testUnmarshalMarshalVoidResult() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(this.getClass().getResourceAsStream("/result-void.xml"), "utf-8");

        // TEST
        final Result<Void> copy = unmarshal(xml, Result.class);

        // VERIFY
        assertThat(copy.getType()).isEqualTo(ResultType.OK);

        // TEST
        final String copyXml = marshal(copy, Result.class);

        // VERIFY
        final Diff documentDiff = DiffBuilder.compare(xml).withTest(copyXml).ignoreWhitespace().build();

        assertThat(documentDiff.hasDifferences()).describedAs(documentDiff.toString()).isFalse();

    }

    @Test
    public final void testUnmarshalMarshalDataResult() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(this.getClass().getResourceAsStream("/result-data.xml"), "utf-8");

        // TEST
        final Result<Node> copy = unmarshal(xml, Result.class);

        // VERIFY
        assertThat(copy.getType()).isEqualTo(ResultType.OK);
        assertThat(copy.getCode()).isNull();
        assertThat(copy.getMessage()).isNull();
        assertThat(copy.getData()).isInstanceOf(Node.class);
        final Node node = (Node) copy.getData();
        assertThat(node.getNodeName()).isEqualTo("invoice-id");
        assertThat(node.getTextContent()).isEqualTo("I-0123456");

        // TEST
        final String copyXml = marshal(copy, Result.class);

        // VERIFY
        final Diff documentDiff = DiffBuilder.compare(xml).withTest(copyXml).ignoreWhitespace().build();

        assertThat(documentDiff.hasDifferences()).describedAs(documentDiff.toString()).isFalse();

    }

    @Test
    public final void testUnmarshalExceptionResult() throws IOException {

        // PREPARE
        final String xml = IOUtils.toString(this.getClass().getResourceAsStream("/result-exception.xml"), "utf-8");

        // TEST
        final Result<Void> copy = unmarshal(xml, Result.class, AggregateNotFoundException.class);

        // VERIFY
        final String msg = "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found";
        assertThat(copy.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(copy.getType()).isEqualTo(ResultType.ERROR);
        assertThat(copy.getMessage()).isEqualTo(msg);
        assertThat(copy.getData()).isNull();
        assertThat(copy.getException()).isInstanceOf(AggregateNotFoundException.class);
        final AggregateNotFoundException anfe = (AggregateNotFoundException) copy.getException();
        assertThat(anfe.getMessage()).isEqualTo(msg);
        assertThat(anfe.getAggregateType()).isEqualTo("Invoice");
        assertThat(anfe.getAggregateId()).isEqualTo("4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119");

        // TEST
        final String copyXml = marshal(copy, Result.class, AggregateNotFoundException.class);

        // VERIFY
        final Diff documentDiff = DiffBuilder.compare(xml).withTest(copyXml).ignoreWhitespace().build();

        assertThat(documentDiff.hasDifferences()).describedAs(documentDiff.toString()).isFalse();

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

    @XmlRootElement(name = "data")
    public static final class Data {

        @XmlValue
        private int id;

        protected Data() {
            super();
        }

        public Data(final int id) {
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
            if (!(obj instanceof Data)) {
                return false;
            }
            Data other = (Data) obj;
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

}
// CHECKSTYLE:ON
