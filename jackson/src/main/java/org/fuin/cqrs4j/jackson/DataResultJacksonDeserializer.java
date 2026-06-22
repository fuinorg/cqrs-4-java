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
package org.fuin.cqrs4j.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.jackson.Objects4JacksonUtils;

import java.io.IOException;

/**
 * Converts an {@link DataResult} from/to JSON.
 */
@ThreadSafe
@SuppressWarnings("rawtypes")
public final class DataResultJacksonDeserializer extends StdDeserializer<DataResult> {

    /**
     * Default constructor.
     */
    public DataResultJacksonDeserializer() {
        super(DataResult.class);
    }

    @Override
    public DataResult deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

        final JsonNode node = jp.getCodec().readTree(jp);

        final ResultType type = ResultType.valueOf(node.get(AbstractResult.TYPE_PROPERTY).asText());
        final String code;
        if (node.has(AbstractResult.CODE_PROPERTY)) {
            code = node.get(AbstractResult.CODE_PROPERTY).asText();
        } else {
            code = null;
        }
        final String message;
        if (node.has(AbstractResult.MESSAGE_PROPERTY)) {
            message = node.get(AbstractResult.MESSAGE_PROPERTY).asText();
        } else {
            message = null;
        }
        if (node.has(DataResult.DATA_CLASS_PROPERTY)) {
            if (!node.has(DataResult.DATA_ELEMENT_PROPERTY)) {
                throw new IllegalStateException(
                        "The '" + DataResult.DATA_ELEMENT_PROPERTY + "' was not found, but is required for deserialization: " + node);
            }
            final String dataClassName = node.get(DataResult.DATA_CLASS_PROPERTY).asText();
            final String dataElement = node.get(DataResult.DATA_ELEMENT_PROPERTY).asText();
            final JsonNode dataNode = node.get(dataElement);
            final Object data = Objects4JacksonUtils.deserialize(jp, ctxt, dataClassName, dataNode);
            return new DataResult<>(type, code, message, data, dataElement);
        }
        return new DataResult<>(type, code, message, null);
    }

}
