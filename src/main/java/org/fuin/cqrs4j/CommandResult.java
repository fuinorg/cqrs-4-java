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

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ExceptionJaxbMarshallable;
import org.fuin.objects4j.common.ExceptionShortIdentifable;
import org.fuin.objects4j.common.Nullable;

/**
 * Result of request. The only convention is to set the <code>code</code> to <code>"0"</code> for a success
 * result with <code>type</code> of {@link ResultType#OK}.
 */
@XmlRootElement(name = "command-result")
public final class CommandResult implements Serializable {

    private static final long serialVersionUID = 1000L;

    @NotNull
    @XmlElement(name = "type")
    private ResultType type;

    @NotNull
    @XmlElement(name = "code")
    private String code;

    @NotNull
    @XmlElement(name = "message")
    private String message;

    @XmlElement(name = "request-created")
    private ZonedDateTime requestCreated;

    @NotNull
    @XmlElement(name = "response-created")
    private ZonedDateTime responseCreated;

    @Valid
    @XmlAnyElement(lax = true)
    private Object data;

    /**
     * Protected default constructor for de-serialization.
     */
    protected CommandResult() {
        super();
    }

    /**
     * Constructor with all data.
     * 
     * @param type
     *            Type.
     * @param code
     *            Code.
     * @param message
     *            Message.
     * @param requestCreated
     *            Date/Time the request was created (if available).
     * @param data
     *            Optional result data.
     */
    public CommandResult(@NotNull final ResultType type, @NotNull final String code,
            @NotNull final String message, @Nullable final ZonedDateTime requestCreated,
            @Nullable final Object data) {
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("code", code);
        Contract.requireArgNotNull("message", message);
        this.type = type;
        this.code = code;
        this.message = message;
        this.requestCreated = requestCreated;
        this.responseCreated = ZonedDateTime.now();
        this.data = data;
    }

    /**
     * Constructor with exception. If the exception is type {@link ExceptionJaxbMarshallable} then it will be
     * used as <code>data</code> field, if not data will be <code>null</code>. An exception of type
     * {@link ExceptionShortIdentifable} will be used to fill the <code>code</code> field with the identifier
     * value. If it's not a {@link ExceptionShortIdentifable} the <code>code</code> field will be set using
     * the full qualified class name of the exception.
     * 
     * @param type
     *            Type.
     * @param exception
     *            The message for the result is equal to the exception message or the simple name of the
     *            exception class if the exception message is <code>null</code>.
     * @param requestCreated
     *            Date/Time the request was created.
     */
    // CHECKSTYLE:OFF:AvoidInlineConditionals
    public CommandResult(@NotNull final ResultType type, @NotNull final Exception exception,
            @Nullable final ZonedDateTime requestCreated) {
        // CHECKSTYLE:ON
        super();
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("exception", exception);
        this.type = type;
        if (exception instanceof ExceptionShortIdentifable) {
            this.code = ((ExceptionShortIdentifable) exception).getShortId();
        } else {
            this.code = exception.getClass().getName();
        }
        if (exception.getMessage() == null) {
            this.message = exception.getClass().getSimpleName();
        } else {
            this.message = exception.getMessage();
        }
        this.requestCreated = requestCreated;
        this.responseCreated = ZonedDateTime.now();
        if (exception instanceof ExceptionJaxbMarshallable) {
            this.data = exception;
        } else {
            this.data = null;
        }
    }

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
    public final String getCode() {
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

    /**
     * Returns optional result data.
     * 
     * @return Additional response data.
     */
    @NotNull
    public final Object getData() {
        return data;
    }

    @Override
    // CHECKSTYLE:OFF Generated code
    public final int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result + ((requestCreated == null) ? 0 : requestCreated.hashCode());
        result = prime * result + ((responseCreated == null) ? 0 : responseCreated.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((data == null) ? 0 : data.hashCode());
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
        final CommandResult other = (CommandResult) obj;
        if (code == null) {
            if (other.code != null) {
                return false;
            }
        } else if (!code.equals(other.code)) {
            return false;
        }
        if (message == null) {
            if (other.message != null) {
                return false;
            }
        } else if (!message.equals(other.message)) {
            return false;
        }
        if (requestCreated == null) {
            if (other.requestCreated != null) {
                return false;
            }
        } else if (!requestCreated.equals(other.requestCreated)) {
            return false;
        }
        if (responseCreated == null) {
            if (other.responseCreated != null) {
                return false;
            }
        } else if (!responseCreated.equals(other.responseCreated)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        if (data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!data.equals(other.data)) {
            return false;
        }
        return true;
    }

    // CHECKSTYLE:ON

    @Override
    public final String toString() {
        return "CommandResult [type=" + type + ", code=" + code + ", message=" + message
                + ", requestCreated=" + requestCreated + ", responseCreated=" + responseCreated + ", data="
                + data + "]";
    }

    /**
     * Creates a success result without any additional data returned.
     * 
     * @param message
     *            Message.
     * @param requestCreated
     *            Date/Time the command was created.
     * 
     * @return OK result.
     */
    public static CommandResult ok(@NotNull final String message, 
            @NotNull final ZonedDateTime requestCreated) {
        return new CommandResult(ResultType.OK, "0", message, requestCreated, null);
    }

}
