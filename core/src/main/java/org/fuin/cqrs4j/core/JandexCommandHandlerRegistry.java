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

import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.utils4j.jandex.JandexIndexFileReader;
import org.fuin.utils4j.jandex.JandexUtils;
import org.jboss.jandex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Uses Jandex files and the local directories to find command handler classes.
 * Command handler classes must meet the following conditions to be included:
 * <ul>
 *     <li>Implement the {@link CommandHandler} interface</li>
 * </ul>
 */
@ThreadSafe
public class JandexCommandHandlerRegistry implements CommandHandlerRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JandexCommandHandlerRegistry.class);

    private final Map<Class<? extends Command>, Class<? extends CommandHandler<?, ?>>> map;

    /**
     * Default constructor.
     */
    public JandexCommandHandlerRegistry() {
        this(new File("target/classes"));
    }

    /**
     * Constructor with classes directories. Most likely only used in tests.
     *
     * @param classesDirs Directories with class files.
     */
    public JandexCommandHandlerRegistry(final File... classesDirs) {
        super();
        final List<File> dirList = Arrays.asList(classesDirs);
        map = new HashMap<>();
        final List<Handler2Command> handlerClasses = scanForClasses(dirList);
        handlerClasses.forEach(handler -> {
            map.put(handler.commandClass(), handler.handlerClass());
            LOG.info("Registered '{}' for: {}", handler.handlerClass().getName(), handler.commandClass().getName());
        });

    }

    @Override
    public Class<? extends CommandHandler<?, ?>> findHandlerClass(Class<? extends Command> cmdClass) {
        final Class<? extends CommandHandler<?, ?>> handlerClass = map.get(cmdClass);
        if (handlerClass == null) {
            throw new CommandHandlerClassNotFoundException(cmdClass);
        }
        return handlerClass;
    }

    private List<Handler2Command> scanForClasses(final List<File> classesDirs) {
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

    private static List<Handler2Command> findClasses(final IndexView index) {
        final Set<ClassInfo> classInfos = new HashSet<>(
                index.getAllKnownImplementations(DotName.createSimple(CommandHandler.class)));
        return new ArrayList<>(classInfos.stream()
                .filter(classInfo -> !Modifier.isAbstract(classInfo.flags())
                        && !Modifier.isInterface(classInfo.flags()))
                .map(classInfo -> JandexUtils.loadClass(classInfo.name()))
                .map(JandexCommandHandlerRegistry::extractCommandType)
                .toList());
    }

    @SuppressWarnings("unchecked")
    private static Handler2Command extractCommandType(Class<?> handlerClass) {
        final String commandName = Arrays.stream(handlerClass.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(intf -> intf.getActualTypeArguments().length == 2)
                .map(intf -> intf.getActualTypeArguments()[0].getTypeName())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot extract Command type from CommandHandler class: " + handlerClass.getName()));
        try {
            final Class<?> cmdClass = Class.forName(commandName);
            return new Handler2Command(
                    (Class<? extends CommandHandler<?, ?>>) handlerClass,
                    (Class<? extends Command>) cmdClass);
        } catch (final ClassNotFoundException ex) {
            throw new IllegalArgumentException("Failed to get class: " + commandName, ex);
        }
    }

    private record Handler2Command(Class<? extends CommandHandler<?, ?>> handlerClass,
                                   Class<? extends Command> commandClass) {
    }

}
