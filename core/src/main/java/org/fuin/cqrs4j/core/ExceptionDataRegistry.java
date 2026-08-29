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
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Optional;

/**
 * Mapping between exception classes and the classes that carry their data to a client.
 * <p>
 * An exception cannot answer this itself: the exception is declared once, while its data class exists
 * per serialization flavour in a module the exception does not depend on.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface ExceptionDataRegistry {

    /**
     * Tries to find the data class for a given exception class.
     * <p>
     * An absent result is the normal case rather than an error: most exceptions carry no data, and a
     * result without any is exactly what should then be sent.
     *
     * @param exceptionClass Exception class to find a data class for.
     * @return Data class, or empty if the exception carries none.
     */
    Optional<Class<? extends ExceptionData<?>>> findDataClass(Class<? extends Exception> exceptionClass);

}
