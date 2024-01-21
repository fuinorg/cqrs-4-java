package org.fuin.cqrs4j;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link JandexDomainEventRegistry} class.
 */
public class JandexDomainEventRegistryTest {

    @Test
    public void testCreate() {
        final JandexDomainEventRegistry testee = new JandexDomainEventRegistry(new File("target/test-classes"));
        assertThat(testee.getDomainEventClasses()).containsOnly(ACreatedEvent.class);
    }

    @Test
    public void testFind() {
        final JandexDomainEventRegistry testee = new JandexDomainEventRegistry(new File("target/test-classes"));
        assertThat(testee.findClass(ACreatedEvent.SER_TYPE)).isEqualTo(ACreatedEvent.class);

    }

}
