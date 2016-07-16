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

import static org.fuin.cqrs4j.Cqrs4JUtils.SHORT_ID_PREFIX;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.fuin.objects4j.common.AbstractJaxbMarshallableException;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ExceptionShortIdentifable;

/**
 * Validating an object failed.
 */
@XmlRootElement(name = "constraint-validation-exception")
@XmlAccessorType(XmlAccessType.NONE)
public final class ConstraintViolationException extends AbstractJaxbMarshallableException implements
        ExceptionShortIdentifable {

    private static final long serialVersionUID = 1L;

    @XmlElement(name = "sid")
    private String sid;

    @XmlElementWrapper(name = "constraint-violations")
    @XmlElement(name = "constraint-violation")
    private List<String> constraintViolations;

    /**
     * JAX-B constructor.
     */
    protected ConstraintViolationException() {
        super();
    }

    /**
     * Constructor with all data.
     * 
     * @param violations
     *            Set of constraint violations.
     */
    // CHECKSTYLE:OFF:AvoidInlineConditionals
    public <T> ConstraintViolationException(@NotNull final Set<ConstraintViolation<T>> violations) {
        super(violations.size() == 1 ? "One constraint violated" : "Multiple constraints violated");
        // CHECKSTYLE:ON
        Contract.requireArgMin("violations.size", violations.size(), 1);
        this.sid = SHORT_ID_PREFIX + "-VALIDATION_FAILED";
        this.constraintViolations = new ArrayList<>();
        for (final ConstraintViolation<T> violation : violations) {
            this.constraintViolations.add(violation.getMessage());
        }
        Collections.sort(constraintViolations);
    }

    @Override
    public final String getShortId() {
        return sid;
    }

    /**
     * Returns the list of constraint violations.
     * 
     * @return Immutable list.
     */
    public final List<String> getConstraintViolations() {
        return Collections.unmodifiableList(constraintViolations);
    }

}
