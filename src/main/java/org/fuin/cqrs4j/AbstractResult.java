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

import org.fuin.objects4j.common.Nullable;
import javax.json.bind.annotation.JsonbProperty;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlElement;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ExceptionShortIdentifable;
import org.fuin.objects4j.ui.Label;
import org.fuin.objects4j.ui.Prompt;
import org.fuin.objects4j.ui.ShortLabel;
import org.fuin.objects4j.ui.Tooltip;

/**
 * Result of a request. The type signals if the execution was successful or not. In case the the result is not {@link ResultType#OK}, the
 * fields code and message should contain unique information to help the user identifying the cause of the problem. A result may carry some
 * optional data.
 * 
 * @param <DATA>
 *            Type of data returned.
 */
public abstract class AbstractResult<DATA> implements Result<DATA>, Serializable {

    private static final long serialVersionUID = 1000L;

    static final String TYPE_PROPERTY = "type"; 
    
    static final String CODE_PROPERTY = "code"; 
    
    static final String MESSAGE_PROPERTY = "message"; 
    
    @Label("Result Type")
    @ShortLabel("TYPE")
    @Tooltip("Type of the result")
    @Prompt("ERROR")
    @NotNull
    @JsonbProperty(TYPE_PROPERTY)
    @XmlElement(name = TYPE_PROPERTY)
    private ResultType type;

    @Label("Result Code")
    @ShortLabel("CODE")
    @Tooltip("Code that uniquely identifies the result. Mostly used in case of warnings or errors.")
    @Prompt("E00001")
    @Nullable
    @JsonbProperty(CODE_PROPERTY)
    @XmlElement(name = CODE_PROPERTY)
    private String code;

    @Label("Result Message")
    @ShortLabel("MSG")
    @Tooltip("Message that describes the result. Mostly used in case of warnings or errors.")
    @Prompt("The field 'Xyz' is mandatory")
    @Nullable
    @JsonbProperty(MESSAGE_PROPERTY)
    @XmlElement(name = MESSAGE_PROPERTY)
    private String message;

    /**
     * Protected default constructor for de-serialization.
     */
    protected AbstractResult() { // NOSONAR Ignore uninitialized fields
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
    public AbstractResult(@NotNull final ResultType type, @Nullable final String code, @Nullable final String message) {
        Contract.requireArgNotNull("type", type);
        this.type = type;
        this.code = code;
        this.message = message;
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
    public AbstractResult(@NotNull final Exception exception) {
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
    }

    @Override
    public final ResultType getType() {
        return type;
    }

    @Override
    public final String getCode() {
        return code;
    }

    @Override
    public final String getMessage() {
        return message;
    }

}
