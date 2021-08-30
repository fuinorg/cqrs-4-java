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

import jakarta.validation.constraints.NotNull;

import org.fuin.objects4j.common.Nullable;

/**
 * Result of a request. The type signals if the execution was successful or not. In case the the result is not {@link ResultType#OK}, the
 * fields code and message should contain unique information to help the user identifying the cause of the problem. A result may carry some
 * optional data.
 * 
 * @param <DATA>
 *            Type of data returned.
 */
public interface Result<DATA> {

    /**
     * Returns the result type.
     * 
     * @return Type.
     */
    @NotNull
    public ResultType getType();

    /**
     * Returns the result code.
     * 
     * @return Code.
     */
    @Nullable
    public String getCode();

    /**
     * Returns the result message.
     * 
     * @return Message.
     */
    @Nullable
    public String getMessage();

    /**
     * Returns the result data.
     * 
     * @return Optional data.
     */
    @Nullable
    public DATA getData();

}
