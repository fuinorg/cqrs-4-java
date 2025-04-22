package org.fuin.cqrs4j.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.objects4j.jackson.Objects4JJacksonAdapterModule;
import org.fuin.utils4j.TestOmitted;

/**
 * Utils for the package.
 */
@TestOmitted("This is only a test class")
final class TestUtils {

    private static final EntityIdFactory ENTITY_ID_FACTORY = new MyIdFactory();

    private TestUtils() {
    }

    /**
     * Creates an instance with the configured values.
     *
     * @return New instance.
     */
    public static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(new Cqrs4JacksonAdapterModule())
                .registerModule(new Objects4JJacksonAdapterModule())
                .registerModule(new Ddd4JacksonModule(ENTITY_ID_FACTORY));
    }

}
