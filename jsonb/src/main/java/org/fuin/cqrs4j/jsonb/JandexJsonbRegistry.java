package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.jsonb.EntityIdJsonbAdapter;
import org.fuin.utils4j.jandex.JandexIndexFileReader;
import org.fuin.utils4j.jandex.JandexUtils;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Registry that is built up by scanning for classes that implement {@link jakarta.json.bind.adapter.JsonbAdapter}.
 * It also cares about specialized {@link EntityIdJsonbAdapter} that require a {@link EntityIdFactory} as constructor argument.
 */
public final class JandexJsonbRegistry implements JsonbRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JandexJsonbRegistry.class);

    private final List<? extends JsonbAdapter<?, ?>> adapters;

    private final List<? extends JsonbSerializer<?>> serializers;

    private final List<? extends JsonbDeserializer<?>> deserializers;

    /**
     * Constructor without classes directory. Assumes that classes are in "target/classes".
     *
     * @param entityIdFactory Factory to use in case an adapter requires it for being constructed.
     */
    public JandexJsonbRegistry(final EntityIdFactory entityIdFactory) {
        this(entityIdFactory, new File("target/classes"));
    }

    /**
     * Constructor with classes directories. Most likely only used in tests.
     *
     * @param entityIdFactory Factory to use in case an adapter requires it for being constructed.
     * @param classesDirs     Directories with class files.
     */
    public JandexJsonbRegistry(final EntityIdFactory entityIdFactory, final File... classesDirs) {
        adapters = findImplementingClasses(JsonbAdapter.class, classesDirs).stream()
                .map(clasz -> (JsonbAdapter<?, ?>) createInstance(entityIdFactory, clasz))
                .filter(Objects::nonNull)
                .toList();
        serializers = findImplementingClasses(JsonbSerializer.class, classesDirs).stream()
                .map(clasz -> (JsonbSerializer<?>) createInstance(entityIdFactory, clasz))
                .filter(Objects::nonNull)
                .toList();
        deserializers = findImplementingClasses(JsonbDeserializer.class, classesDirs).stream()
                .map(clasz -> (JsonbDeserializer<?>) createInstance(entityIdFactory, clasz))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<? extends JsonbAdapter<?, ?>> getAdapters() {
        return adapters;
    }

    @Override
    public List<? extends JsonbSerializer<?>> getSerializers() {
        return serializers;
    }

    @Override
    public List<? extends JsonbDeserializer<?>> getDeserializers() {
        return deserializers;
    }

    @SuppressWarnings("unchecked")
    private static <T> T createInstance(final EntityIdFactory entityIdFactory, final Class<?> clasz) {
        final Constructor<?>[] constructors = clasz.getConstructors();
        if (constructors.length == 0) {
            LOG.warn("No public constructor found: {}", clasz.getName());
        } else {
            for (final Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    return createInstance(clasz, () -> (T) constructor.newInstance());
                } else if (constructor.getParameterCount() == 1
                        && EntityIdFactory.class.isAssignableFrom(constructor.getParameterTypes()[0])) {
                    return createInstance(clasz, () -> (T) constructor.newInstance(entityIdFactory));
                }
            }
        }
        return null;
    }

    private static List<Class<?>> findImplementingClasses(final Class<?> intf, final File... classesDirs) {
        final List<IndexView> indexes = new ArrayList<>();
        indexes.add(new JandexIndexFileReader.Builder().addDefaultResource().build().loadR());
        indexes.add(indexClassesDirs(classesDirs));
        return findImplementingClasses(intf, CompositeIndex.create(indexes));
    }

    private static IndexView indexClassesDirs(final File... classesDirs) {
        final Indexer indexer = new Indexer();
        final List<File> knownClassFiles = new ArrayList<>();
        for (final File classesDir : classesDirs) {
            JandexUtils.indexDir(indexer, knownClassFiles, classesDir);
        }
        return indexer.complete();
    }

    private static List<Class<?>> findImplementingClasses(final Class<?> intf, final IndexView index) {
        List<Class<?>> implementors = new ArrayList<>();
        final Collection<ClassInfo> implementingClasses = index.getAllKnownImplementors(DotName.createSimple(intf));
        for (final ClassInfo classInfo : implementingClasses) {
            if (!Modifier.isAbstract(classInfo.flags()) && !Modifier.isInterface(classInfo.flags())) {
                final Class<?> implementor = JandexUtils.loadClass(classInfo.name());
                implementors.add(implementor);
                LOG.info("Added {} to {}: {}", intf.getSimpleName(), JandexJsonbRegistry.class.getSimpleName(), implementor.getName());
            }
        }
        return implementors;
    }

    private static <T> T createInstance(final Class<?> clasz, final NewInstanceSupplier<T> supplier) {
        try {
            return supplier.supply();
        } catch (final InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            LOG.error("Failed to instantiate {}", clasz.getName(), ex);
            return null;
        }
    }

    private interface NewInstanceSupplier<T> {

        T supply() throws InstantiationException, IllegalAccessException, InvocationTargetException;

    }

}
