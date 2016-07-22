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
 * Result of a request. The type signals if the execution was successful or not. In case the the result is not
 * {@link ResultType#OK}, the fields code and message should contain unique information to help the user
 * identifying the cause of the problem.
 */
@XmlRootElement(name = "result")
public final class Result implements Serializable {

    private static final long serialVersionUID = 1000L;

    @NotNull
    @XmlElement(name = "type")
    private ResultType type;

    @Nullable
    @XmlElement(name = "code")
    private String code;

    @Nullable
    @XmlElement(name = "message")
    private String message;

    @Valid
    @XmlAnyElement(lax = true)
    private Object data;

    /**
     * Protected default constructor for de-serialization.
     */
    protected Result() {
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
     * @param data
     *            Optional result data.
     */
    public Result(@NotNull final ResultType type, @Nullable final String code,
            @Nullable final String message, @Nullable final Object data) {
        Contract.requireArgNotNull("type", type);
        this.type = type;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Constructor with exception. If the exception is type {@link ExceptionJaxbMarshallable} then it will be
     * used as <code>data</code> field, if not data will be <code>null</code>. An exception of type
     * {@link ExceptionShortIdentifable} will be used to fill the <code>code</code> field with the identifier
     * value. If it's not a {@link ExceptionShortIdentifable} the <code>code</code> field will be set using
     * the full qualified class name of the exception.
     * 
     * @param exception
     *            The message for the result is equal to the exception message or the simple name of the
     *            exception class if the exception message is <code>null</code>.
     */
    // CHECKSTYLE:OFF:AvoidInlineConditionals
    public Result(@NotNull final Exception exception) {
        // CHECKSTYLE:ON
        super();
        Contract.requireArgNotNull("exception", exception);
        this.type = ResultType.ERROR;
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
        final Result other = (Result) obj;
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
        return "Result [type=" + type + ", code=" + code + ", message=" + message + ", data=" + data + "]";
    }

    /**
     * Create a success result without any data.
     * 
     * @return Result with type {@link ResultType#OK}.
     */
    public static Result ok() {
        return new Result(ResultType.OK, null, null, null);
    }

    /**
     * Create a success result with some data.
     * 
     * @param data
     *            Optional data.
     * 
     * @return Result with type {@link ResultType#OK}.
     */
    public static Result ok(@Nullable final Object data) {
        return new Result(ResultType.OK, null, null, data);
    }

}
