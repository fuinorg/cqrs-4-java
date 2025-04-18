package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.adapter.JsonbAdapter;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.jboss.weld.environment.deployment.discovery.jandex.Jandex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link JandexJsonbRegistry} class.
 */
class JandexJsonbRegistryTest {

    @Test
    void getAllLists() {

        final EntityIdFactory entityIdFactory = new JandexEntityIdFactory();
        final JandexJsonbRegistry testee = new JandexJsonbRegistry(entityIdFactory);

        assertThat(testee.getAdapters()).isNotEmpty();
        assertThat(testee.getSerializers()).isNotEmpty();
        assertThat(testee.getDeserializers()).isNotEmpty();

    }
}