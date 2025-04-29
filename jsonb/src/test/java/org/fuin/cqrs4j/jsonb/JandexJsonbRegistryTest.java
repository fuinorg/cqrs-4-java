package org.fuin.cqrs4j.jsonb;

import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.esc.api.DeserializerRegistry;
import org.fuin.esc.api.SerializerRegistry;
import org.fuin.objects4j.jsonb.JsonbProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link JandexJsonbRegistry} class.
 */
@ExtendWith(MockitoExtension.class)
class JandexJsonbRegistryTest {

    @Mock
    private EntityIdFactory entityIdFactory;

    @Mock
    private SerializerRegistry serializerRegistry;

    @Mock
    private DeserializerRegistry deserializerRegistry;

    @Mock
    private JsonbProvider jsonbProvider;

    @Test
    void getAllLists() {

        final JandexJsonbRegistry testee = new JandexJsonbRegistry(entityIdFactory, serializerRegistry,
                deserializerRegistry, jsonbProvider);

        assertThat(testee.getAdapters()).isNotEmpty();
        assertThat(testee.getSerializers()).isNotEmpty();
        assertThat(testee.getDeserializers()).isNotEmpty();

    }

    @Test
    void testParameters4() throws NoSuchMethodException {

        final Constructor<Foo4> constructor = Foo4.class.getConstructor(EntityIdFactory.class,
                SerializerRegistry.class, DeserializerRegistry.class, JsonbProvider.class);
        final Optional<Object[]> parameters = JandexJsonbRegistry.parameters(entityIdFactory,
                serializerRegistry,
                deserializerRegistry, jsonbProvider, constructor);
        assertThat(parameters).isPresent();
        assertThat(parameters.get()).isEqualTo(new Object[]{entityIdFactory, serializerRegistry,
                deserializerRegistry, jsonbProvider});

    }

    @Test
    void testParameters3() throws NoSuchMethodException {

        final Constructor<Foo3> constructor = Foo3.class.getConstructor(JsonbProvider.class,
                DeserializerRegistry.class, SerializerRegistry.class);
        final Optional<Object[]> parameters = JandexJsonbRegistry.parameters(entityIdFactory,
                serializerRegistry, deserializerRegistry, jsonbProvider, constructor);
        assertThat(parameters).isPresent();
        assertThat(parameters.get()).isEqualTo(new Object[]{jsonbProvider,
                deserializerRegistry, serializerRegistry});

    }

    @Test
    void testParameters2() throws NoSuchMethodException {

        final Constructor<Foo2> constructor = Foo2.class.getConstructor(
                DeserializerRegistry.class, SerializerRegistry.class);
        final Optional<Object[]> parameters = JandexJsonbRegistry.parameters(entityIdFactory,
                serializerRegistry, deserializerRegistry, jsonbProvider, constructor);
        assertThat(parameters).isPresent();
        assertThat(parameters.get()).isEqualTo(new Object[]{
                deserializerRegistry, serializerRegistry});

    }

    @Test
    void testParameters1() throws NoSuchMethodException {

        final Constructor<Foo1> constructor = Foo1.class.getConstructor(JsonbProvider.class);
        final Optional<Object[]> parameters = JandexJsonbRegistry.parameters(entityIdFactory,
                serializerRegistry, deserializerRegistry, jsonbProvider, constructor);
        assertThat(parameters).isPresent();
        assertThat(parameters.get()).isEqualTo(new Object[]{jsonbProvider});

    }

    @Test
    void testParameters0() throws NoSuchMethodException {

        final Constructor<Foo0> constructor = Foo0.class.getConstructor();
        final Optional<Object[]> parameters = JandexJsonbRegistry.parameters(entityIdFactory,
                serializerRegistry, deserializerRegistry, jsonbProvider, constructor);
        assertThat(parameters).isPresent();
        assertThat(parameters.get()).isEqualTo(new Object[]{});

    }

    @Test
    void testCreateInstance4() throws NoSuchMethodException {
        assertThat((Object) JandexJsonbRegistry.createInstance(
                entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, Foo4.class))
                .isInstanceOf(Foo4.class);
    }

    @Test
    void testCreateInstance3() throws NoSuchMethodException {
        assertThat((Object) JandexJsonbRegistry.createInstance(
                entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, Foo3.class))
                .isInstanceOf(Foo3.class);
    }

    @Test
    void testCreateInstance2() throws NoSuchMethodException {
        assertThat((Object) JandexJsonbRegistry.createInstance(
                entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, Foo2.class))
                .isInstanceOf(Foo2.class);
    }

    @Test
    void testCreateInstance1() throws NoSuchMethodException {
        assertThat((Object) JandexJsonbRegistry.createInstance(
                entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, Foo1.class))
                .isInstanceOf(Foo1.class);
    }

    @Test
    void testCreateInstance0() throws NoSuchMethodException {
        assertThat((Object) JandexJsonbRegistry.createInstance(
                entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider, Foo0.class))
                .isInstanceOf(Foo0.class);
    }

    @Test
    void testParameter() {

        assertThat(JandexJsonbRegistry.parameter(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider,
                EntityIdFactory.class)).hasValue(entityIdFactory);
        assertThat(JandexJsonbRegistry.parameter(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider,
                SerializerRegistry.class)).hasValue(serializerRegistry);
        assertThat(JandexJsonbRegistry.parameter(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider,
                DeserializerRegistry.class)).hasValue(deserializerRegistry);
        assertThat(JandexJsonbRegistry.parameter(entityIdFactory, serializerRegistry, deserializerRegistry, jsonbProvider,
                Integer.class)).isEmpty();

    }

    public static class Foo4 {
        public Foo4(EntityIdFactory entityIdFactory,
                    SerializerRegistry serializerRegistry,
                    DeserializerRegistry deserializerRegistry,
                    JsonbProvider jsonbProvider) {
        }
    }

    public static class Foo3 {
        public Foo3(JsonbProvider jsonbProvider,
                    DeserializerRegistry deserializerRegistry,
                    SerializerRegistry serializerRegistry) {
        }
    }

    public static class Foo2 {
        public Foo2(DeserializerRegistry deserializerRegistry,
                    SerializerRegistry serializerRegistry) {
        }
    }

    public static class Foo1 {
        public Foo1(JsonbProvider jsonbProvidery) {
        }
    }

    public static class Foo0 {
        public Foo0() {
            // Test
        }
    }

}