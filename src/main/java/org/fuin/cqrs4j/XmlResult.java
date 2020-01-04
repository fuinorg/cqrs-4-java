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

import org.fuin.objects4j.common.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ExceptionJaxbMarshallable;
import org.fuin.objects4j.common.ExceptionShortIdentifable;
import org.fuin.objects4j.ui.Label;
import org.fuin.objects4j.ui.Prompt;
import org.fuin.objects4j.ui.ShortLabel;
import org.fuin.objects4j.ui.Tooltip;

/**
 * Result of a request. The type signals if the execution was successful or not. In case the the result is not {@link ResultType#OK}, the
 * fields code and message should contain unique information to help the user identifying the cause of the problem.
 * 
 * @param <DATA>
 *            Type of data returned in case of success (type = {@link ResultType#OK}).
 */
@XmlRootElement(name = "result")
public final class XmlResult<DATA> extends AbstractResult<DATA> {

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
    protected XmlResult() { // NOSONAR Ignore uninitialized fields
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
    public XmlResult(@NotNull final ResultType type, @Nullable final String code, @Nullable final String message,
            @Nullable final DATA data) {
        super(type, code, message);
        this.data = data;
    }

    /**
     * Constructor with exception. If the exception is type {@link ExceptionJaxbMarshallable} then it will be used as <code>data</code>
     * field, if not data will be <code>null</code>. An exception of type {@link ExceptionShortIdentifable} will be used to fill the
     * <code>code</code> field with the identifier value. If it's not a {@link ExceptionShortIdentifable} the <code>code</code> field will
     * be set using the full qualified class name of the exception.
     * 
     * @param exception
     *            The message for the result is equal to the exception message or the simple name of the exception class if the exception
     *            message is <code>null</code>.
     */
    // CHECKSTYLE:OFF:AvoidInlineConditionals
    public XmlResult(@NotNull final Exception exception) {
        // CHECKSTYLE:ON
        super(exception);
        if (exception instanceof ExceptionJaxbMarshallable) {
            this.data = exception;
        } else {
            this.data = null;
        }
    }

    /**
     * Returns the result data if {@link #getType()} is {@link ResultType#OK} or {@link ResultType#WARNING} and <code>null</code> in all
     * other cases.
     * 
     * @return Response data.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public final DATA getData() {
        if ((getType() == ResultType.OK) || (getType() == ResultType.WARNING)) {
            return (DATA) data;
        }
        return null;
    }

    /**
     * Returns the exception if {@link #getType()} is {@link ResultType#ERROR} or <code>null</code> in all other cases.
     * 
     * @return Exception that caused the error.
     */
    @Nullable
    public final Exception getException() {
        if (getType() == ResultType.ERROR) {
            return (Exception) data;
        }
        return null;
    }

    @Override
    // CHECKSTYLE:OFF Generated code
    public final int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getCode() == null) ? 0 : getCode().hashCode());
        result = prime * result + ((getMessage() == null) ? 0 : getMessage().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
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
        final XmlResult<?> other = (XmlResult<?>) obj;
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
        if (getType() != other.getType()) {
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
        return "Result [type=" + getType() + ", code=" + getCode() + ", message=" + getMessage() + "]";
    }

    /**
     * Create a success result without any data.
     * 
     * @return Result with type {@link ResultType#OK}.
     */
    public static XmlResult<Void> ok() {
        return new XmlResult<Void>(ResultType.OK, null, null, null);
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
    public static <T> XmlResult<T> ok(@Nullable final T data) {
        return new XmlResult<T>(ResultType.OK, null, null, data);
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
    public static <T> XmlResult<T> error(@NotNull final String code, @NotNull final String message) {
        Contract.requireArgNotNull("code", code);
        Contract.requireArgNotNull("message", message);
        return new XmlResult<T>(ResultType.ERROR, code, message, null);
    }

}
