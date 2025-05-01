package org.fuin.cqrs4j.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fuin.utils4j.TestOmitted;

import java.util.List;

/**
 * Module that registers the adapters for the package.
 */
@TestOmitted("Tested with other tests")
public class Cqrs4JacksonModule extends Module {

    @Override
    public String getModuleName() {
        return "Cqrs4JModule";
    }

    @Override
    public Iterable<? extends Module> getDependencies() {
        return List.of(new JavaTimeModule());
    }

    @Override
    public void setupModule(SetupContext context) {
        final SimpleSerializers serializers = new SimpleSerializers();
        serializers.addSerializer(new DataResultJacksonSerializer());
        context.addSerializers(serializers);

        final SimpleDeserializers deserializers = new SimpleDeserializers();
        deserializers.addDeserializer(DataResult.class, new DataResultJacksonDeserializer());
        context.addDeserializers(deserializers);
    }

    @Override
    public Version version() {
        // Don't forget to change from release to SNAPSHOT and back!
        return new Version(0, 6, 0, "SNAPSHOT",
                "org.fuin.cqrs4j", "cqrs-4-java-jackson");
    }

}