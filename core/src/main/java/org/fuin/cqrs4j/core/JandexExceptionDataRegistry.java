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
import org.jspecify.annotations.Nullable;
import org.fuin.utils4j.jandex.JandexIndexFileReader;
import org.fuin.utils4j.jandex.JandexUtils;
import org.jboss.jandex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * Uses Jandex files and the local directories to find exception data classes.
 * Exception data classes must meet the following conditions to be included:
 * <ul>
 *     <li>Implement the {@link ExceptionData} interface</li>
 *     <li>Name the exception they carry as the type argument</li>
 * </ul>
 * A class the index does not reach is simply not registered, and the exception it belongs to then
 * produces a result without data - which is why the scan has to see both sides of the wire.
 */
@ThreadSafe
public class JandexExceptionDataRegistry implements ExceptionDataRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JandexExceptionDataRegistry.class);

    private final Map<Class<? extends Exception>, Class<? extends ExceptionData<?>>> map;

    /**
     * Default constructor.
     */
    public JandexExceptionDataRegistry() {
        this(new File("target/classes"));
    }

    /**
     * Constructor with classes directories. Most likely only used in tests.
     *
     * @param classesDirs Directories with class files.
     */
    public JandexExceptionDataRegistry(final File... classesDirs) {
        super();
        final List<File> dirList = Arrays.asList(classesDirs);
        map = new HashMap<>();
        final List<Data2Exception> dataClasses = scanForClasses(dirList);
        dataClasses.forEach(data -> {
            map.put(data.exceptionClass(), data.dataClass());
            LOG.info("Registered '{}' for: {}", data.dataClass().getName(), data.exceptionClass().getName());
        });
    }

    @Override
    public Optional<Class<? extends ExceptionData<?>>> findDataClass(final Class<? extends Exception> exceptionClass) {
        return Optional.ofNullable(map.get(exceptionClass));
    }

    private List<Data2Exception> scanForClasses(final List<File> classesDirs) {
        final List<IndexView> indexes = new ArrayList<>();
        indexes.add(new JandexIndexFileReader.Builder().addDefaultResource().build().loadR());
        indexes.add(indexClassesDirs(classesDirs));
        return findClasses(CompositeIndex.create(indexes));
    }

    private IndexView indexClassesDirs(final List<File> classesDirs) {
        final Indexer indexer = new Indexer();
        final List<File> knownClassFiles = new ArrayList<>();
        for (final File classesDir : classesDirs) {
            JandexUtils.indexDir(indexer, knownClassFiles, classesDir);
        }
        return indexer.complete();
    }

    private static List<Data2Exception> findClasses(final IndexView index) {
        final Set<ClassInfo> classInfos = new HashSet<>(
                index.getAllKnownImplementations(DotName.createSimple(ExceptionData.class)));
        return new ArrayList<>(classInfos.stream()
                .filter(classInfo -> !Modifier.isAbstract(classInfo.flags())
                        && !Modifier.isInterface(classInfo.flags()))
                .map(classInfo -> JandexUtils.loadClass(classInfo.name()))
                .map(JandexExceptionDataRegistry::extractExceptionType)
                .flatMap(Optional::stream)
                .toList());
    }

    /**
     * The exception a data class carries, read off its {@link ExceptionData} type argument.
     * <p>
     * A class that implements the interface without saying which exception it is for is skipped rather
     * than rejected: it may be an intermediate base of somebody else's hierarchy, and refusing to build
     * the registry at all would take every other exception's data down with it.
     */
    @SuppressWarnings("unchecked")
    private static Optional<Data2Exception> extractExceptionType(final Class<?> dataClass) {
        final Optional<Class<?>> exClass = exceptionTypeOf(dataClass);
        if (exClass.isEmpty()) {
            LOG.debug("Skipped '{}': it names no exception type", dataClass.getName());
            return Optional.empty();
        }
        return Optional.of(new Data2Exception(
                (Class<? extends ExceptionData<?>>) dataClass,
                (Class<? extends Exception>) exClass.get()));
    }

    /**
     * Walks up from a data class to wherever {@link ExceptionData} is parameterized.
     * <p>
     * It is rarely the class itself: the convention is an abstract base per family of exceptions -
     * {@code AggregateNotFoundExceptionData extends AbstractAggregateExceptionData&lt;AggregateNotFoundException&gt;} -
     * so the interface is implemented one level up and its argument is a type variable the subclass
     * binds. Reading only the direct interfaces finds none of those.
     */
    private static Optional<Class<?>> exceptionTypeOf(final Class<?> dataClass) {
        Class<?> raw = dataClass;
        Type asSeenFromSubclass = null;
        while (raw != null && raw != Object.class) {
            for (final Type implemented : raw.getGenericInterfaces()) {
                if (implemented instanceof ParameterizedType parameterized
                        && parameterized.getRawType() == ExceptionData.class
                        && parameterized.getActualTypeArguments().length == 1) {
                    return bound(parameterized.getActualTypeArguments()[0], raw, asSeenFromSubclass);
                }
            }
            asSeenFromSubclass = raw.getGenericSuperclass();
            raw = raw.getSuperclass();
        }
        return Optional.empty();
    }

    /**
     * The class a type argument stands for: itself when written out, and what the subclass bound it to
     * when it is a type variable of the declaring class.
     */
    private static Optional<Class<?>> bound(final Type argument, final Class<?> declaring,
                                            @Nullable final Type asSeenFromSubclass) {
        if (argument instanceof Class<?> written) {
            return Optional.of(written);
        }
        if (argument instanceof TypeVariable<?> variable && asSeenFromSubclass instanceof ParameterizedType parameterized) {
            final TypeVariable<?>[] parameters = declaring.getTypeParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].equals(variable) && parameterized.getActualTypeArguments()[i] instanceof Class<?> actual) {
                    return Optional.of(actual);
                }
            }
        }
        return Optional.empty();
    }

    private record Data2Exception(Class<? extends ExceptionData<?>> dataClass,
                                  Class<? extends Exception> exceptionClass) {
    }

}
