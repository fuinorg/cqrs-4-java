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
package org.fuin.cqrs4j.esc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.eclipse.yasson.FieldAccessStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link CommandMeta}.
 * <p>
 * The class carries no serialization annotations on purpose - this module has neither Jackson nor JSON-B
 * on its compile classpath - so the two round trips below are the only thing standing between that
 * decision and an application discovering at runtime that its stack cannot bind the type.
 */
class CommandMetaTest {

    private static final String SUBJECT_ID = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6";

    @Test
    void testJacksonRoundTrip() throws Exception {

        // PREPARE - field visibility as the ESC object mapper configures it
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        // TEST
        final String json = mapper.writeValueAsString(new CommandMeta(SUBJECT_ID));
        final CommandMeta copy = mapper.readValue(json, CommandMeta.class);

        // VERIFY
        assertThat(json).isEqualTo("{\"subjectId\":\"" + SUBJECT_ID + "\"}");
        assertThat(copy.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(copy).isEqualTo(new CommandMeta(SUBJECT_ID));

    }

    @Test
    void testJsonbRoundTrip() throws Exception {

        // PREPARE - field access, as TestUtils.createJsonbConfig() sets it up for the ESC JSON-B stack.
        // A default-configured Jsonb writes the value out through the getter and then silently drops it
        // on the way back in, because it has no setter to put it back through.
        final JsonbConfig config = new JsonbConfig().withPropertyVisibilityStrategy(new FieldAccessStrategy());
        try (final Jsonb jsonb = JsonbBuilder.create(config)) {

            // TEST
            final String json = jsonb.toJson(new CommandMeta(SUBJECT_ID));
            final CommandMeta copy = jsonb.fromJson(json, CommandMeta.class);

            // VERIFY
            assertThat(json).isEqualTo("{\"subjectId\":\"" + SUBJECT_ID + "\"}");
            assertThat(copy.getSubjectId()).isEqualTo(SUBJECT_ID);
            assertThat(copy).isEqualTo(new CommandMeta(SUBJECT_ID));

        }

    }

    /**
     * The type name is what {@code Repository.add(..., metaType, ...)} passes and what the registry is
     * looked up by, so the two constants must not drift apart.
     */
    @Test
    void testTypeAndSerializedTypeAgree() {
        assertThat(CommandMeta.SER_TYPE.asBaseType()).isEqualTo(CommandMeta.TYPE.asBaseType());
        assertThat(CommandMeta.TYPE.asBaseType()).isEqualTo("CommandMeta");
    }

    /**
     * An event with no acting user is worse than a loud failure - it looks attributed and is not.
     */
    @Test
    void testSubjectIdIsMandatory() {
        assertThatThrownBy(() -> new CommandMeta(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("subjectId==null");
    }

    /**
     * Guards the promise made in the class Javadoc: nothing but the opaque subject id may be stored, so
     * that deleting the user in the identity provider actually erases the personal data.
     */
    @Test
    void testNothingButTheSubjectIdIsSerialized() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        assertThat(mapper.readTree(mapper.writeValueAsString(new CommandMeta(SUBJECT_ID))).fieldNames())
                .toIterable().containsExactly("subjectId");
    }

}
