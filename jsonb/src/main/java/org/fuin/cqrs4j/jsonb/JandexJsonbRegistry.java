package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.jsonb.EntityIdJsonbAdapter;
import org.fuin.esc.api.DeserializerRegistry;
import org.fuin.esc.api.SerializerRegistry;
import org.fuin.objects4j.jsonb.JsonbProvider;
import org.fuin.utils4j.jandex.JandexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
     * @param entityIdFactory      Factory to use in case an adapter requires it for being constructed.
     * @param serializerRegistry   Serializer registry used to construct serializers/deserializers.
     * @param deserializerRegistry Deserializer registry used to construct serializers/deserializers.
     * @param jsonbProvider        Provides an JSON-B instance.
     */
    public JandexJsonbRegistry(final EntityIdFactory entityIdFactory,
                               final SerializerRegistry serializerRegistry,
                               final DeserializerRegistry deserializerRegistry,
                               final JsonbProvider jsonbProvider) {
        this(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, new File("target/classes"));
    }

    /**
     * Constructor with classes directories. Most likely only used in tests.
     *
     * @param entityIdFactory      Factory to use in case an adapter requires it for being constructed.
     * @param serializerRegistry   Serializer registry used to construct serializers/deserializers.
     * @param deserializerRegistry Deserializer registry used to construct serializers/deserializers.
     * @param jsonbProvider        Provides an JSON-B instance.
     * @param classesDirs          Directories with class files.
     */
    public JandexJsonbRegistry(final EntityIdFactory entityIdFactory,
                               final SerializerRegistry serializerRegistry,
                               final DeserializerRegistry deserializerRegistry,
                               final JsonbProvider jsonbProvider,
                               final File... classesDirs) {
        adapters = JandexUtils.findImplementors(JsonbAdapter.class, classesDirs).stream()
                .peek(adapter -> LOG.info("Found {}: {}", JsonbAdapter.class.getSimpleName(), adapter))
                .map(clasz -> (JsonbAdapter<?, ?>) createInstance(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, clasz))
                .filter(Objects::nonNull)
                .toList();
        serializers = JandexUtils.findImplementors(JsonbSerializer.class, classesDirs).stream()
                .peek(serializer -> LOG.info("Found {}: {}", JsonbSerializer.class.getSimpleName(), serializer))
                .map(clasz -> (JsonbSerializer<?>) createInstance(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, clasz))
                .filter(Objects::nonNull)
                .toList();
        deserializers = JandexUtils.findImplementors(JsonbDeserializer.class, classesDirs).stream()
                .peek(deserializer -> LOG.info("Found {}: {}", JsonbDeserializer.class.getSimpleName(), deserializer))
                .map(clasz -> (JsonbDeserializer<?>) createInstance(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, clasz))
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
    static <T> T createInstance(final EntityIdFactory entityIdFactory,
                                final SerializerRegistry serializerRegistry,
                                final DeserializerRegistry deserializerRegistry,
                                final JsonbProvider jsonbProvider,
                                final Class<?> clasz) {

        final Constructor<?>[] constructors = clasz.getConstructors();
        if (constructors.length == 0) {
            LOG.warn("No public constructor found: {}", clasz.getName());
        } else {
            for (final Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    return createInstance(clasz, () -> (T) constructor.newInstance());
                } else {
                    final Optional<Object[]> args = parameters(entityIdFactory, serializerRegistry,
                            deserializerRegistry, jsonbProvider, constructor);
                    if (args.isPresent()) {
                        return createInstance(clasz, () -> (T) constructor.newInstance(args.get()));
                    }
                }
            }
            throw new IllegalArgumentException("Didn't find an appropriate constructor for: '" + clasz.getName());
        }
        return null;
    }

    static Optional<Object[]> parameters(final EntityIdFactory entityIdFactory,
                                         final SerializerRegistry serializerRegistry,
                                         final DeserializerRegistry deserializerRegistry,
                                         final JsonbProvider jsonbProvider,
                                         final Constructor<?> constructor) {
        final Object[] args = new Object[constructor.getParameterCount()];
        for (int i = 0; i < constructor.getParameterCount(); i++) {
            final Optional<Object> param = parameter(entityIdFactory, serializerRegistry, deserializerRegistry,
                    jsonbProvider, constructor.getParameterTypes()[i]);
            if (param.isPresent()) {
                args[i] = param.get();
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(args);
    }

    static Optional<Object> parameter(final EntityIdFactory entityIdFactory,
                                      final SerializerRegistry serializerRegistry,
                                      final DeserializerRegistry deserializerRegistry,
                                      final JsonbProvider jsonbProvider,
                                      Class<?> parameterType) {
        if (EntityIdFactory.class.isAssignableFrom(parameterType)) {
            return Optional.of(entityIdFactory);
        }
        if (SerializerRegistry.class.isAssignableFrom(parameterType)) {
            return Optional.of(serializerRegistry);
        }
        if (DeserializerRegistry.class.isAssignableFrom(parameterType)) {
            return Optional.of(deserializerRegistry);
        }
        if (JsonbProvider.class.isAssignableFrom(parameterType)) {
            return Optional.of(jsonbProvider);
        }
        return Optional.empty();
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
