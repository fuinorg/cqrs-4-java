package org.fuin.cqrs4j.quarkus.view;

import org.fuin.cqrs4j.esc.ConverterRegistration;
import org.fuin.esc.api.Converter;
import org.fuin.esc.api.ConverterRegistry;
import org.fuin.esc.api.SerializedDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link QuarkusConverterRegistryProducer} class.
 */
public class QuarkusConverterRegistryProducerTest {

    private static final SerializedDataType TYPE = new SerializedDataType("MyEvent");

    @Test
    public void testProducesWorkingRegistry() {

        // PREPARE
        final QuarkusConverterRegistryProducer testee = new QuarkusConverterRegistryProducer();
        final List<ConverterRegistration> registrations = List.of(
                new ConverterRegistration(TYPE, "1", "2", append("|v2")));

        // TEST
        final ConverterRegistry registry = testee.converterRegistry(registrations);

        // VERIFY
        assertThat((Object) registry.upcastToLatest(TYPE, "1", "data")).isEqualTo("data|v2");

    }

    @Test
    public void testEmptyIsPassThrough() {

        // TEST
        final ConverterRegistry registry = new QuarkusConverterRegistryProducer().converterRegistry(List.of());

        // VERIFY
        assertThat((Object) registry.upcastToLatest(TYPE, "1", "data")).isEqualTo("data");

    }

    private static Converter<String, String> append(final String suffix) {
        return new Converter<>() {
            @Override
            public Class<String> getSourceType() {
                return String.class;
            }

            @Override
            public Class<String> getTargetType() {
                return String.class;
            }

            @Override
            public String convert(final String source) {
                return source + suffix;
            }
        };
    }

}
