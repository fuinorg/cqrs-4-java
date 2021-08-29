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

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ExceptionShortIdentifable;
import org.fuin.objects4j.common.Nullable;

/**
 * Result of a request. The type signals if the execution was successful or not. In case the the result is not {@link ResultType#OK}, the
 * fields code and message should contain unique information to help the user identifying the cause of the problem. A simple result does not
 * carry any additional data.
 */
@XmlRootElement(name = "result")
public final class SimpleResult extends AbstractResult<Void> {

    private static final long serialVersionUID = 1000L;

    /**
     * Protected default constructor for de-serialization.
     */
    protected SimpleResult() { // NOSONAR Ignore uninitialized fields
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
     */
    public SimpleResult(@NotNull final ResultType type, @Nullable final String code, @Nullable final String message) {
        super(type, code, message);
    }

    /**
     * Constructor with exception. An exception of type {@link ExceptionShortIdentifable} will be used to fill the <code>code</code> field
     * with the identifier value. If it's not a {@link ExceptionShortIdentifable} the <code>code</code> field will be set using the full
     * qualified class name of the exception.
     * 
     * @param exception
     *            The message for the result is equal to the exception message or the simple name of the exception class if the exception
     *            message is <code>null</code>.
     */
    // CHECKSTYLE:OFF:AvoidInlineConditionals
    public SimpleResult(@NotNull final Exception exception) {
        // CHECKSTYLE:ON
        super(exception);
    }

    @Override
    public Void getData() {
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
        final SimpleResult other = (SimpleResult) obj;
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
        return true;
    }

    // CHECKSTYLE:ON

    @Override
    public final String toString() {
        return "Result [type=" + getType() + ", code=" + getCode() + ", message=" + getMessage() + "]";
    }

    /**
     * Create a success result.
     * 
     * @return Result with type {@link ResultType#OK}.
     */
    public static SimpleResult ok() {
        return new SimpleResult(ResultType.OK, null, null);
    }

    /**
     * Create a warning result.
     * 
     * @param code
     *            Code.
     * @param message
     *            Message.
     * 
     * @return Result with type {@link ResultType#WARNING}.
     */
    public static SimpleResult warning(@NotNull final String code, @NotNull final String message) {
        Contract.requireArgNotNull("code", code);
        Contract.requireArgNotNull("message", message);
        return new SimpleResult(ResultType.WARNING, code, message);
    }

    /**
     * Create an error result.
     * 
     * @param code
     *            Code.
     * @param message
     *            Message.
     * 
     * @return Result with type {@link ResultType#ERROR}.
     */
    public static SimpleResult error(@NotNull final String code, @NotNull final String message) {
        Contract.requireArgNotNull("code", code);
        Contract.requireArgNotNull("message", message);
        return new SimpleResult(ResultType.ERROR, code, message);
    }

}
