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

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ExceptionJaxbMarshallable;
import org.fuin.objects4j.common.ExceptionShortIdentifable;
import javax.annotation.Nullable;
import org.fuin.objects4j.ui.Label;
import org.fuin.objects4j.ui.Prompt;
import org.fuin.objects4j.ui.ShortLabel;
import org.fuin.objects4j.ui.Tooltip;

/**
 * Result of a request. The type signals if the execution was successful or not.
 * In case the the result is not {@link ResultType#OK}, the fields code and
 * message should contain unique information to help the user identifying the
 * cause of the problem.
 * 
 * @param <DATA>
 *            Type of data returned in case of success (type =
 *            {@link ResultType#OK}).
 */
@XmlRootElement(name = "result")
public final class Result<DATA> {

    @Label("Result Type")
    @ShortLabel("TYPE")
    @Tooltip("Type of the result")
    @Prompt("ERROR")
    @NotNull
    @XmlElement(name = "type")
    private ResultType type;

    @Label("Result Code")
    @ShortLabel("CODE")
    @Tooltip("Code that uniquely identifies the result. Mostly used in case of warnings or errors.")
    @Prompt("E00001")
    @Nullable
    @XmlElement(name = "code")
    private String code;

    @Label("Result Message")
    @ShortLabel("MSG")
    @Tooltip("Message that describes the result. Mostly used in case of warnings or errors.")
    @Prompt("The field 'Xyz' is mandatory")
    @Nullable
    @XmlElement(name = "message")
    private String message;

    @Label("Data")
    @ShortLabel("DATA")
    @Tooltip("Optional result data")
    @Prompt("Optional Data")
    @Valid
    @XmlAnyElement(lax = true)
    private Object data;

    /**
     * Protected default constructor for de-serialization.
     */
    protected Result() { //NOSONAR Ignore uninitialized fields
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
    public Result(@NotNull final ResultType type, @Nullable final String code, @Nullable final String message,
            @Nullable final DATA data) {
        Contract.requireArgNotNull("type", type);
        this.type = type;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Constructor with exception. If the exception is type
     * {@link ExceptionJaxbMarshallable} then it will be used as
     * <code>data</code> field, if not data will be <code>null</code>. An
     * exception of type {@link ExceptionShortIdentifable} will be used to fill
     * the <code>code</code> field with the identifier value. If it's not a
     * {@link ExceptionShortIdentifable} the <code>code</code> field will be set
     * using the full qualified class name of the exception.
     * 
     * @param exception
     *            The message for the result is equal to the exception message
     *            or the simple name of the exception class if the exception
     *            message is <code>null</code>.
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
            this.message = "";
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
     * Returns the result data if {@link #getType()} is {@link ResultType#OK} or
     * {@link ResultType#WARNING} and <code>null</code> in all other cases.
     * 
     * @return Response data.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public final DATA getData() {
        if ((type == ResultType.OK) || (type == ResultType.WARNING)) {
            return (DATA) data;
        }
        return null;
    }

    /**
     * Returns the exception if {@link #getType()} is {@link ResultType#ERROR}
     * or <code>null</code> in all other cases.
     * 
     * @return Exception that caused the error.
     */
    @Nullable
    public final Exception getException() {
        if (type == ResultType.ERROR) {
            return (Exception) data;
        }
        return null;
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
        final Result<?> other = (Result<?>) obj;
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
        return "Result [type=" + type + ", code=" + code + ", message=" + message + "]";
    }

    /**
     * Create a success result without any data.
     * 
     * @return Result with type {@link ResultType#OK}.
     */
    public static Result<Void> ok() {
        return new Result<Void>(ResultType.OK, null, null, null);
    }

    /**
     * Create a success result with some data.
     * 
     * @param data
     *            Optional data.
     * 
     * @return Result with type {@link ResultType#OK}.
     * 
     * @param <T>
     *            Type of data.
     */
    public static <T> Result<T> ok(@Nullable final T data) {
        return new Result<T>(ResultType.OK, null, null, data);
    }

    /**
     * Create an error result without any data.
     * 
     * @param code
     *            Code.
     * @param message
     *            Message.
     * 
     * @return Error result with.
     * 
     * @param <T>
     *            Not used.
     */
    public static <T> Result<T> error(@NotNull final String code, @NotNull final String message) {
        Contract.requireArgNotNull("code", code);
        Contract.requireArgNotNull("message", message);
        return new Result<T>(ResultType.ERROR, code, message, null);
    }

}
