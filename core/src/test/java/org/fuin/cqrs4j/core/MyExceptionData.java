/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.ExceptionData;

import org.fuin.utils4j.TestOmitted;

import java.io.Serial;

/**
 * Data of {@link MyException}, used to test the {@link JandexExceptionDataRegistry}.
 */
@TestOmitted("Test helper class")
public final class MyExceptionData implements ExceptionData<MyException> {

    @Serial
    private static final long serialVersionUID = 1000L;

    private String message;

    /**
     * Constructor only for marshalling/unmarshalling.
     */
    protected MyExceptionData() {
        super();
    }

    /**
     * Constructor with all data.
     *
     * @param ex Exception to copy data from.
     */
    public MyExceptionData(final MyException ex) {
        super();
        this.message = ex.getMessage();
    }

    @Override
    public String getDataElement() {
        return "my-exception";
    }

    @Override
    public MyException toException() {
        return new MyException(message);
    }

}
