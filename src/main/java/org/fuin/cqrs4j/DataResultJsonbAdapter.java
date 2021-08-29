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

import static org.fuin.cqrs4j.AbstractResult.CODE_PROPERTY;
import static org.fuin.cqrs4j.AbstractResult.MESSAGE_PROPERTY;
import static org.fuin.cqrs4j.AbstractResult.TYPE_PROPERTY;
import static org.fuin.cqrs4j.DataResult.DATA_CLASS_PROPERTY;
import static org.fuin.cqrs4j.DataResult.DATA_ELEMENT_PROPERTY;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.bind.Jsonb;
import javax.json.bind.adapter.JsonbAdapter;
import javax.validation.constraints.NotNull;

import org.fuin.objects4j.common.Contract;

/**
 * Converts an {@link DataResult} from/to JSON.
 */
@SuppressWarnings("rawtypes")
public final class DataResultJsonbAdapter implements JsonbAdapter<DataResult, JsonObject> {

    private final Jsonb jsonb;

    /**
     * Constructor with jsonb instance.
     * 
     * @param jsonb
     *            Jsonb instance used to marshal/unmarshal the data object.
     */
    public DataResultJsonbAdapter(@NotNull final Jsonb jsonb) {
        super();
        Contract.requireArgNotNull("jsonb", jsonb);
        this.jsonb = jsonb;
    }

    @Override
    public JsonObject adaptToJson(final DataResult result) throws Exception {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(TYPE_PROPERTY, result.getType().name());
        if (result.getCode() != null) {
            builder.add(CODE_PROPERTY, result.getCode());
        }
        if (result.getMessage() != null) {
            builder.add(MESSAGE_PROPERTY, result.getMessage());
        }
        if (result.getData() != null) {
            builder.add(DATA_CLASS_PROPERTY, result.getData().getClass().getName());
            final String json = jsonb.toJson(result.getData());
            final String elName = result.getDataElement();
            if (elName == null) {
                throw new IllegalStateException("The 'dataElementName' was empty, but is required fro JSON-B: " + result);
            }
            builder.add(DATA_ELEMENT_PROPERTY, result.getDataElement());
            builder.add(elName, unmarshal(json));
        }
        return builder.build();
    }

    @Override
    public DataResult adaptFromJson(final JsonObject jsonObj) throws Exception {
        final ResultType type = ResultType.valueOf(jsonObj.getString(TYPE_PROPERTY));
        final String code = getString(jsonObj, CODE_PROPERTY);
        final String message = getString(jsonObj, MESSAGE_PROPERTY);
        if (jsonObj.containsKey(DATA_CLASS_PROPERTY)) {
            if (!jsonObj.containsKey(DATA_ELEMENT_PROPERTY)) {
                throw new IllegalStateException(
                        "The '" + DATA_ELEMENT_PROPERTY + "' was not found, but is required fro JSON-B: " + jsonObj);
            }
            final Class<?> dataClass = Class.forName(jsonObj.getString(DATA_CLASS_PROPERTY));
            final String dataElement = getString(jsonObj, DATA_ELEMENT_PROPERTY);
            final JsonObject data = jsonObj.getJsonObject(dataElement);
            final String json = marshal(data);
            final Object obj = jsonb.fromJson(json, dataClass);
            return new DataResult<>(type, code, message, obj, dataElement);
        }
        return new DataResult<>(type, code, message, null);
    }

    private String marshal(final JsonObject jsonObj) {
        try (final StringWriter sw = new StringWriter(); final JsonWriter writer = Json.createWriter(sw)) {
            writer.write(jsonObj);
            return sw.toString();
        } catch (final IOException ex) {
            throw new RuntimeException("Failed to marshal JSON object to string: " + jsonObj, ex);
        }
    }

    private JsonObject unmarshal(final String json) {
        try (final JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }

    }

    private String getString(final JsonObject jsonObj, final String name) {
        if (jsonObj.containsKey(name)) {
            return jsonObj.getString(name);
        }
        return null;
    }

}
