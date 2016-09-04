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

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotNull;

import org.fuin.objects4j.common.Contract;
import org.junit.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

/**
 * Tests for {@link ConstraintViolationException}.
 */
// CHECKSTYLE:OFF Disabled for test
public class ConstraintViolationExceptionTest {

    @Test
    public void testSerializeDeserialize() {

        // PREPARE
        final Set<ConstraintViolation<Dummy>> violations = Contract.getValidator().validate(new Dummy());
        final ConstraintViolationException original = new ConstraintViolationException(violations);

        // TEST
        final byte[] data = serialize(original);
        final ConstraintViolationException copy = deserialize(data);

        // VERIFY
        assertThat(copy.getShortId()).isEqualTo(original.getShortId());
        assertThat(copy.getMessage()).isEqualTo(original.getMessage());
        assertThat(copy.getConstraintViolations()).containsExactlyElementsOf(
                original.getConstraintViolations());

    }

    @Test
    public final void testMarshalUnmarshalXML() throws Exception {

        // PREPARE
        final Set<ConstraintViolation<Dummy>> violations = Contract.getValidator().validate(new Dummy());
        final ConstraintViolationException original = new ConstraintViolationException(violations);

        // TEST
        final String xml = marshal(original, ConstraintViolationException.class);

        // VERIFY
        final Diff documentDiff = DiffBuilder
                .compare(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                                + "<constraint-violation-exception>"
                                + "<msg>Multiple constraints violated</msg>"
                                + "<sid>org.fuin.cqrs4j.ConstraintViolationException</sid>" + "<constraint-violations>"
                                + "<constraint-violation>A is required</constraint-violation>"
                                + "<constraint-violation>B cannot be empty</constraint-violation>"
                                + "</constraint-violations>" + "</constraint-violation-exception>")
                .withTest(xml).ignoreWhitespace().build();

        assertThat(documentDiff.hasDifferences()).describedAs(documentDiff.toString()).isFalse();

        // TEST
        final ConstraintViolationException copy = unmarshal(xml, ConstraintViolationException.class);

        // VERIFY
        assertThat(copy.getShortId()).isEqualTo(original.getShortId());
        assertThat(copy.getMessage()).isEqualTo(original.getMessage());
        assertThat(copy.getConstraintViolations()).containsExactlyElementsOf(
                original.getConstraintViolations());

    }

    private static class Dummy {

        @NotNull(message = "A is required")
        private String a;

        @NotNull(message = "B cannot be empty")
        private String b;

    }

}
// CHECKSTYLE:ON
