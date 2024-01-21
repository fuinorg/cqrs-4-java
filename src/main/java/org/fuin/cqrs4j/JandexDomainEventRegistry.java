package org.fuin.cqrs4j;

import jakarta.validation.constraints.NotNull;
import org.fuin.ddd4j.ddd.DomainEvent;
import org.fuin.ddd4j.ddd.JandexEntityIdFactory;
import org.fuin.esc.api.*;
import org.fuin.utils4j.JandexIndexFileReader;
import org.fuin.utils4j.JandexUtils;
import org.jboss.jandex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Registry that is built up by scanning for classes that are annotated with
 * {@link HasSerializedDataTypeConstant} and implement {@link DomainEvent},
 * but not {@link AggregateCommand}.
 */
public class JandexDomainEventRegistry implements SerializedDataTypeRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JandexDomainEventRegistry.class);

    private static final DotName COMMAND_INTERFACE = DotName.createSimple(Command.class);

    private final SimpleSerializedDataTypeRegistry delegate;

    private final List<File> classesDirs;

    private final List<Class<?>> domainEventClasses;

    /**
     * Default constructor.
     */
    public JandexDomainEventRegistry() {
        this(new File("target/classes"));
    }

    /**
     * Constructor with classes directories. Most likely only used in tests.
     *
     * @param classesDirs Directories with class files.
     */
    public JandexDomainEventRegistry(final File... classesDirs) {
        delegate = new SimpleSerializedDataTypeRegistry();
        this.classesDirs = Arrays.asList(classesDirs);
        domainEventClasses = scanForDomainEventClasses();
        for (final Class<?> domainEventClass : domainEventClasses) {
            delegate.add(serializedDataTypeConstant(domainEventClass), domainEventClass);
        }
    }

    @Override
    @NotNull
    public Class<?> findClass(@NotNull SerializedDataType type) {
        return delegate.findClass(type);
    }

    /**
     * Returns a list of known {@link DomainEvent} classes.
     *
     * @return Domain event classes.
     */
    public List<Class<?>> getDomainEventClasses() {
        return Collections.unmodifiableList(domainEventClasses);
    }

    private List<Class<?>> scanForDomainEventClasses() {
        final List<IndexView> indexes = new ArrayList<>();
        indexes.add(new JandexIndexFileReader.Builder().addDefaultResource().build().loadR());
        indexes.add(indexClassesDirs());
        return findDomainEventClasses(CompositeIndex.create(indexes));
    }

    private IndexView indexClassesDirs() {
        final Indexer indexer = new Indexer();
        final List<File> knownClassFiles = new ArrayList<>();
        for (final File classesDir : classesDirs) {
            JandexUtils.indexDir(indexer, knownClassFiles, classesDir);
        }
        return indexer.complete();
    }

    private static List<Class<?>> findDomainEventClasses(final IndexView index) {
        List<Class<?>> classes = new ArrayList<>();
        final Collection<ClassInfo> classInfos = index.getAllKnownImplementors(DotName.createSimple(DomainEvent.class));
        for (final ClassInfo classInfo : classInfos) {
            if (!Modifier.isAbstract(classInfo.flags())
                    && !Modifier.isInterface(classInfo.flags())) {

                final Class<?> clasz = JandexUtils.loadClass(classInfo.name());
                if (!AggregateCommand.class.isAssignableFrom(clasz)) {
                    boolean include = true;
                    if (clasz.getAnnotation(HasSerializedDataTypeConstant.class) == null) {
                        LOG.warn("Missing annotation @{} on {} class: {}", HasSerializedDataTypeConstant.class.getSimpleName(), DomainEvent.class.getSimpleName(), clasz.getName());
                        include = false;
                    }
                    if (include) {
                        classes.add(clasz);
                        LOG.info("Added {} class to {}: {}", DomainEvent.class.getSimpleName(), JandexEntityIdFactory.class.getSimpleName(), clasz.getName());
                    } else {
                        LOG.debug("Ignored {} class: {}", DomainEvent.class.getSimpleName(), clasz.getName());
                    }
                }
            }
        }
        return classes;
    }

    public SerializedDataType serializedDataTypeConstant(Class<?> domainEventClass) {
        final HasSerializedDataTypeConstant annotation = domainEventClass.getAnnotation(HasSerializedDataTypeConstant.class);
        return HasSerializedDataTypeConstantValidator.extractValue(domainEventClass, annotation.value());
    }

}
