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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.fuin.utils4j.TestOmitted;

import java.io.IOException;

/**
 * Converts an {@link DataResult} from/to JSON.
 */
@SuppressWarnings("rawtypes")
@TestOmitted("Tested with other tests")
public final class DataResultJacksonSerializer extends StdSerializer<DataResult> {

    /**
     * Default constructor.
     */
    public DataResultJacksonSerializer() {
        super(DataResult.class);
    }

    @Override
    public void serialize(DataResult result, JsonGenerator generator,
                          SerializerProvider provider) throws IOException {

        generator.writeStartObject();
        generator.writeStringField(AbstractResult.TYPE_PROPERTY, result.getType().name());
        if (result.getCode() != null) {
            generator.writeStringField(AbstractResult.CODE_PROPERTY, result.getCode());
        }
        if (result.getMessage() != null) {
            generator.writeStringField(AbstractResult.MESSAGE_PROPERTY, result.getMessage());
        }
        if (result.getData() != null) {
            generator.writeStringField(DataResult.DATA_CLASS_PROPERTY, result.getData().getClass().getName());
            final String elName = result.getDataElement();
            if (elName == null) {
                throw new IllegalStateException("The 'dataElementName' was empty, but is required for serialization: " + result);
            }
            generator.writeStringField(DataResult.DATA_ELEMENT_PROPERTY, result.getDataElement());
            generator.writeObjectField(elName, result.getData());
        }
        generator.writeEndObject();
    }

}
