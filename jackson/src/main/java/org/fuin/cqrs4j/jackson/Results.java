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
package org.fuin.cqrs4j.jackson;

import org.fuin.cqrs4j.core.ExceptionDataRegistry;
import org.fuin.cqrs4j.core.JandexExceptionDataRegistry;
import org.fuin.cqrs4j.core.Result;
import org.fuin.ddd4j.core.ExceptionData;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Turns an exception into the result sent back for it.
 * <p>
 * One place decides, so that a command handler only says <em>that</em> the operation was refused and
 * never which data class carries the refusal. The <code>code</code> identifies the kind of problem -
 * the exception's full qualified class name, which is unique across every library without anybody
 * maintaining a numbering scheme - and the data carries what the refusal was about.
 */
@ThreadSafe
public final class Results {

    private static final Logger LOG = LoggerFactory.getLogger(Results.class);

    private Results() {
        throw new UnsupportedOperationException("It's not allowed to create an instance of this utility class");
    }

    /**
     * Returns the result for an exception, using the registry built from the classpath.
     *
     * @param exception Exception to express as a result.
     * @return Result with data when the exception has a data class, without it otherwise.
     *
     * @param <T> Type of data.
     */
    public static <T> Result<T> error(final Exception exception) {
        return error(exception, DefaultRegistry.INSTANCE);
    }

    /**
     * Returns the result for an exception, using the given registry.
     *
     * @param exception Exception to express as a result.
     * @param registry Mapping from exception classes to their data classes.
     * @return Result with data when the exception has a data class, without it otherwise.
     *
     * @param <T> Type of data.
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(final Exception exception, final ExceptionDataRegistry registry) {
        Contract.requireArgNotNull("exception", exception);
        Contract.requireArgNotNull("registry", registry);
        final Optional<Class<? extends ExceptionData<?>>> dataClass = registry.findDataClass(exception.getClass());
        if (dataClass.isPresent()) {
            final Optional<ExceptionData<?>> data = create(dataClass.get(), exception);
            if (data.isPresent()) {
                return (Result<T>) new DataResult<>(data.get());
            }
        }
        return (Result<T>) new SimpleResult(exception);
    }

    /**
     * Creates the data for an exception, or empty when it cannot be created.
     * <p>
     * A refusal that cannot carry its data is still a refusal, so the failure is logged and the caller
     * falls back to a result without data rather than losing the refusal itself. The log names both
     * classes, because the cause is always the same: a data class whose constructor does not take the
     * exception it is registered for.
     */
    private static Optional<ExceptionData<?>> create(final Class<? extends ExceptionData<?>> dataClass,
                                                     final Exception exception) {
        try {
            return Optional.of(dataClass.getConstructor(exception.getClass()).newInstance(exception));
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            LOG.error("Failed to create '{}' for: {}", dataClass.getName(), exception.getClass().getName(), ex);
            return Optional.empty();
        }
    }

    private static final class DefaultRegistry {
        private static final ExceptionDataRegistry INSTANCE = new JandexExceptionDataRegistry();
    }

}
