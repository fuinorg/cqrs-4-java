package org.fuin.cqrs4j.springboot.test.app;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import org.fuin.cqrs4j.springboot.test.model.PersonId;
import org.fuin.cqrs4j.springboot.test.model.PersonName;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.jackson.EntityIdJacksonDeserializer;
import org.fuin.ddd4j.jackson.EntityIdJacksonSerializer;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.jackson.ValueObjectStringJacksonDeserializer;
import org.fuin.objects4j.jackson.ValueObjectStringJacksonSerializer;

import java.util.Objects;

/**
 * Jackson module that has the test classes.
 */
@ThreadSafe
public final class TestModelJacksonModule extends Module {

    private final EntityIdFactory entityIdFactory;

    public TestModelJacksonModule(EntityIdFactory entityIdFactory) {
        this.entityIdFactory = Objects.requireNonNull(entityIdFactory, "entityIdFactory==null");
    }

    public String getModuleName() {
        return "Cqrs4JavaTest";
    }

    @Override
    public void setupModule(Module.SetupContext context) {

        final SimpleSerializers serializers = new SimpleSerializers();
        serializers.addSerializer(new EntityIdJacksonSerializer<>(PersonId.class));
        serializers.addSerializer(PersonName.class, new ValueObjectStringJacksonSerializer<>(PersonName.class));
        context.addSerializers(serializers);

        final SimpleDeserializers deserializers = new SimpleDeserializers();
        deserializers.addDeserializer(PersonId.class, new EntityIdJacksonDeserializer<>(PersonId.class, entityIdFactory));
        deserializers.addDeserializer(PersonName.class, new ValueObjectStringJacksonDeserializer<>(PersonName.class, PersonName::new));
        context.addDeserializers(deserializers);
    }

    public Version version() {
        return new Version(0, 6, 0, "",
                "org.fuin.cqrs4j", "cqrs-4-java-test"
        );
    }

}
