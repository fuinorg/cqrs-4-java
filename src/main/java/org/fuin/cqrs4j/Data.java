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

import javax.json.bind.annotation.JsonbProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.fuin.objects4j.common.Contract;

/**
 * Container for any data that allows JSON and XML deserializers to peek the type of content using the "content-type" field.
 *
 * @param <CONTENT>
 *            Type of content inside the "data" element returned in case of success (type = {@link ResultType#OK}).
 */
@XmlRootElement(name = "data")
public final class Data<CONTENT> {

    @NotNull
    @JsonbProperty("content-type")
    @XmlElement(name = "content-type")
    private String contentType;

    @NotNull
    @Valid
    @XmlAnyElement(lax = true)
    private CONTENT contentData;

    /**
     * Protected default constructor for de-serialization.
     */
    protected Data() { // NOSONAR Ignore uninitialized fields
        super();
    }

    /**
     * Constructor with mandatory data.
     * 
     * @param contentType
     *            Unique type of the content data field.
     * @param contentData
     *            Data.
     */
    public Data(@NotNull final String contentType, @NotNull @Valid final CONTENT contentData) {
        super();
        Contract.requireArgNotNull("contentType", contentType);
        Contract.requireArgNotNull("contentData", contentData);
        this.contentType = contentType;
        this.contentData = contentData;
    }

    /**
     * Returns the unique type of the content data field.
     * 
     * @return Type of the data.
     */
    public final String getContentType() {
        return contentType;
    }

    /**
     * Returns the data.
     * 
     * @return Data.
     */
    public final CONTENT getContentData() {
        return contentData;
    }

    @Override
    // CHECKSTYLE:OFF Generated code
    public final int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((contentData == null) ? 0 : contentData.hashCode());
        result = prime * result + ((contentType == null) ? 0 : contentType.hashCode());
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Data<CONTENT> other = (Data<CONTENT>) obj;
        if (contentData == null) {
            if (other.contentData != null) {
                return false;
            }
        } else if (!contentData.equals(other.contentData)) {
            return false;
        }
        if (contentType == null) {
            if (other.contentType != null) {
                return false;
            }
        } else if (!contentType.equals(other.contentType)) {
            return false;
        }
        return true;
    }
    // CHECKSTYLE:ON

    @Override
    public String toString() {
        return "Data [contentType=" + contentType + "]";
    }

}
