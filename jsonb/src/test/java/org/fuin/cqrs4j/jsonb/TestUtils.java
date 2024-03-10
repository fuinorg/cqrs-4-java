package org.fuin.cqrs4j.jsonb;

import com.tngtech.archunit.junit.ArchIgnore;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.BinaryDataStrategy;
import org.eclipse.yasson.FieldAccessStrategy;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.jsonb.EntityIdPathJsonbAdapter;

import java.nio.charset.StandardCharsets;

/**
 * Utils for the package.
 */
@ArchIgnore
final class TestUtils {

    private TestUtils() {
    }

    /**
     * Creates an instance with the configured values.
     *
     * @return New instance.
     */
    public static Jsonb jsonb() {
        final EntityIdFactory factory = new MyIdFactory();
        return JsonbBuilder.create(
                new JsonbConfig()
                        .withEncoding(StandardCharsets.UTF_8.name())
                        .withPropertyVisibilityStrategy(new FieldAccessStrategy())
                        .withAdapters(new EntityIdPathJsonbAdapter(factory))
                        .withBinaryDataStrategy(BinaryDataStrategy.BASE_64)
        );
    }

}
