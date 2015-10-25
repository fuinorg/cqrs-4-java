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

import java.io.Serializable;
import java.time.ZonedDateTime;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAttribute;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.Nullable;

/**
 * Result of request.
 */
public abstract class AbstractResult implements Serializable {

    private static final long serialVersionUID = 1000L;

    @NotNull
    @XmlAttribute(name = "type")
    private ResultType type;

    @NotNull
    @XmlAttribute(name = "code")
    private Long code;

    @NotNull
    @XmlAttribute(name = "message")
    private String message;

    @NotNull
    @XmlAttribute(name = "request-created")
    private ZonedDateTime requestCreated;

    @NotNull
    @XmlAttribute(name = "response-created")
    private ZonedDateTime responseCreated;

    /**
     * Protected default constructor for de-serialization.
     */
    protected AbstractResult() {
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
     *            Date/Time the request was created (if available).
     */
    public AbstractResult(@NotNull final ResultType type, @NotNull final Long code,
            @NotNull final String message, @Nullable final ZonedDateTime requestCreated) {
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("code", code);
        Contract.requireArgNotNull("message", message);
        this.type = type;
        this.code = code;
        this.message = message;
        this.requestCreated = requestCreated;
        this.responseCreated = ZonedDateTime.now();
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
    // CHECKSTYLE:OFF:AvoidInlineConditionals
    public AbstractResult(@NotNull final ResultType type, @NotNull final Long code,
            @NotNull final Exception exception, @NotNull final ZonedDateTime requestCreated) {
        this(type, code, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception
                .getMessage(), requestCreated);
    }

    // CHECKSTYLE:ON

    /**
     * Returns the result type.
     * 
     * @return Type.
     */
    @NotNull
    public final ResultType getType() {
        return type;
    }

    /**
     * Returns the result code.
     * 
     * @return Code.
     */
    @NotNull
    public final Long getCode() {
        return code;
    }

    /**
     * Returns the result message.
     * 
     * @return Message.
     */
    @NotNull
    public final String getMessage() {
        return message;
    }

    /**
     * Returns date/time the original request was created.
     * 
     * @return Timestamp.
     */
    @Nullable
    public final ZonedDateTime getRequestCreated() {
        return requestCreated;
    }

    /**
     * Returns date/time the response was created.
     * 
     * @return Timestamp.
     */
    @NotNull
    public final ZonedDateTime getResponseCreated() {
        return responseCreated;
    }

}
