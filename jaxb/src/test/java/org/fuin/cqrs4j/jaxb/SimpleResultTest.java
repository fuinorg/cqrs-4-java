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
package org.fuin.cqrs4j.jaxb;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.core.StringBasedEntityType;
import org.fuin.utils4j.jaxb.JaxbUtils;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.io.Serial;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public final class SimpleResultTest {

    private static final EntityType TEST_TYPE = new StringBasedEntityType("Test");

    @Test
    public final void testEqualsHashCode() {
        EqualsVerifier.simple().forClass(SimpleResult.class).verify();
    }

    @Test
    public final void testConstructorAll() {

        // TEST
        final SimpleResult testee = new SimpleResult(ResultType.WARNING, "X1", "Yes!");

        // VERIFY
        assertThat(testee.getType()).isEqualTo(ResultType.WARNING);
        assertThat(testee.getCode()).isEqualTo("X1");
        assertThat(testee.getMessage()).isEqualTo("Yes!");

    }

    @Test
    public final void testConstructorException() {

        // PREPARE
        final TestId id = new TestId();
        final AggregateNotFoundException ex = new AggregateNotFoundException(TEST_TYPE, id);

        // TEST
        final SimpleResult testee = new SimpleResult(ex);

        // VERIFY
        assertThat(testee.getType()).isEqualTo(ResultType.ERROR);
        assertThat(testee.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(testee.getMessage()).isEqualTo(TEST_TYPE + " with id " + id.asString() + " not found");

    }

    @Test
    public final void testMarshalUnmarshal() {

        // PREPARE
        final SimpleResult original = SimpleResult.ok();

        // TEST
        final String xml = JaxbUtils.marshal(original, SimpleResult.class);
        final SimpleResult copy = JaxbUtils.unmarshal(xml, SimpleResult.class);

        // VERIFY
        assertThat(original).isEqualTo(copy);

    }

    @Test
    public final void testUnmarshalMarshalOkResult() throws IOException {

        // PREPARE
        final String xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <result>
                    <type>OK</type>
                </result>
                """;

        // TEST
        final SimpleResult copy = JaxbUtils.unmarshal(xml, SimpleResult.class);

        // VERIFY
        assertThat(copy.getType()).isEqualTo(ResultType.OK);

        // TEST
        final String copyXml = JaxbUtils.marshal(copy, SimpleResult.class);

        // VERIFY
        final Diff documentDiff = DiffBuilder.compare(xml).withTest(copyXml).ignoreWhitespace().build();

        assertThat(documentDiff.hasDifferences()).describedAs(documentDiff.toString()).isFalse();

    }

    @Test
    public final void testUnmarshalExceptionResult() throws IOException {

        // PREPARE
        final String xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <result>
                    <type>ERROR</type>
                    <code>DDD4J-AGGREGATE_NOT_FOUND</code>
                    <message>Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found</message>
                </result>
                """;

        // TEST
        final SimpleResult copy = JaxbUtils.unmarshal(xml, SimpleResult.class);

        // VERIFY
        final String msg = "Invoice with id 4dcf4c2c-10e1-4db9-ba9e-d1e644e9d119 not found";
        assertThat(copy.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(copy.getType()).isEqualTo(ResultType.ERROR);
        assertThat(copy.getMessage()).isEqualTo(msg);

        // TEST
        final String copyXml = JaxbUtils.marshal(copy, SimpleResult.class, AggregateNotFoundException.class);

        // VERIFY
        final Diff documentDiff = DiffBuilder.compare(xml).withTest(copyXml).ignoreWhitespace().build();

        assertThat(documentDiff.hasDifferences()).describedAs(documentDiff.toString()).isFalse();

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
