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

import java.time.ZonedDateTime;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Void result of a request that has no specific data.
 */
@XmlRootElement(name = "void-result")
public final class VoidResult extends AbstractResult {

    private static final long serialVersionUID = 1000L;

    /**
     * Protected default constructor for de-serialization.
     */
    protected VoidResult() {
        super();
    }

    /**
     * Constructor with all mandatory data.
     * 
     * @param type
     *            Type.
     * @param code
     *            Code.
     * @param message
     *            Message.
     * @param requestCreated
     *            Date/Time the request was created.
     */
    public VoidResult(@NotNull final ResultType type, @NotNull final Long code,
            @NotNull final String message, @NotNull final ZonedDateTime requestCreated) {
        super(type, code, message, requestCreated);
    }

    /**
     * Constructor with exception.
     * 
     * @param type
     *            Type.
     * @param code
     *            Code.
     * @param exception
     *            The message for the result is equal to the exception message or the simple name of the
     *            exception class if the exception message is <code>null</code>.
     * @param requestCreated
     *            Date/Time the request was created.
     */
    public VoidResult(@NotNull final ResultType type, @NotNull final Long code,
            @NotNull final Exception exception, @NotNull final ZonedDateTime requestCreated) {
        super(type, code, exception, requestCreated);
    }

    @Override
    // CHECKSTYLE:OFF Generated code
    public final int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getCode() == null) ? 0 : getCode().hashCode());
        result = prime * result + ((getMessage() == null) ? 0 : getMessage().hashCode());
        result = prime * result + ((getRequestCreated() == null) ? 0 : getRequestCreated().hashCode());
        result = prime * result + ((getResponseCreated() == null) ? 0 : getResponseCreated().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
        return result;
    }

    @Override
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
        final AbstractResult other = (AbstractResult) obj;
        if (getCode() == null) {
            if (other.getCode() != null) {
                return false;
            }
        } else if (!getCode().equals(other.getCode())) {
            return false;
        }
        if (getMessage() == null) {
            if (other.getMessage() != null) {
                return false;
            }
        } else if (!getMessage().equals(other.getMessage())) {
            return false;
        }
        if (getRequestCreated() == null) {
            if (other.getRequestCreated() != null) {
                return false;
            }
        } else if (!getRequestCreated().equals(other.getRequestCreated())) {
            return false;
        }
        if (getResponseCreated() == null) {
            if (other.getResponseCreated() != null) {
                return false;
            }
        } else if (!getResponseCreated().equals(other.getResponseCreated())) {
            return false;
        }
        if (getType() != other.getType()) {
            return false;
        }
        return true;
    }

    // CHECKSTYLE:ON

    @Override
    public final String toString() {
        return "VoidResult [type=" + getType() + ", code=" + getCode() + ", message=" + getMessage()
                + ", requestCreated=" + getRequestCreated() + ", responseCreated=" + getResponseCreated()
                + "]";
    }

}
