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

import static org.fest.assertions.Assertions.assertThat;
import static org.fuin.utils4j.JaxbUtils.marshal;
import static org.fuin.utils4j.JaxbUtils.unmarshal;
import static org.fuin.utils4j.Utils4J.deserialize;
import static org.fuin.utils4j.Utils4J.serialize;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.apache.commons.io.IOUtils;
import org.fuin.ddd4j.ddd.AggregateNotFoundException;
import org.junit.Test;
import org.w3c.dom.Node;

// CHECKSTYLE:OFF
public final class CommandResultTest {

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

}
// CHECKSTYLE:ON
