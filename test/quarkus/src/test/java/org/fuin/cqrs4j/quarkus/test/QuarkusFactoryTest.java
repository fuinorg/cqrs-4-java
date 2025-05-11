package org.fuin.cqrs4j.quarkus.test;

import jakarta.json.bind.JsonbConfig;
import org.fuin.cqrs4j.jsonb.JsonbRegistry;
import org.fuin.cqrs4j.quarkus.test.app.QuarkusFactory;
import org.fuin.cqrs4j.quarkus.test.model.PersonCreatedEvent;
import org.fuin.cqrs4j.quarkus.test.model.PersonId;
import org.fuin.cqrs4j.quarkus.test.model.PersonName;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.SerDeserializerRegistry;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.esc.jsonb.JsonbSerDeserializer;
import org.fuin.objects4j.jsonb.JsonbProvider;
import org.fuin.utils4j.Utils4J;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.cqrs4j.quarkus.test.QuarkusTestHelper.commonEvent;
import static org.fuin.cqrs4j.quarkus.test.QuarkusTestHelper.personCreatedEvent;

/**
 * Test for the {@link QuarkusFactory} class.
 */
public class QuarkusFactoryTest {

    @Test
    public void testToJson() {

        final QuarkusFactory testee = new QuarkusFactory();
        final EntityIdFactory entityIdFactory = testee.entityIdFactory();
        final JsonbConfig jsonbConfig = testee.jsonbConfig();
        final JsonbProvider jsonbProvider = testee.jsonbProvider(jsonbConfig);
        final SerializedDataTypeRegistry typeRegistry = testee.serializedDataTypeRegistry();
        final JsonbSerDeserializer jsonbSerDeserializer = testee.jsonbSerDeserializer(jsonbProvider, typeRegistry);
        final SerDeserializerRegistry serDeserializerRegistry = testee.serDeserializerRegistry(jsonbConfig, jsonbProvider, entityIdFactory, typeRegistry, jsonbSerDeserializer);
        // We don't need the "serDeserializerRegistry" in this test,
        // but it also adds the JSON-B adapters/serializers/deserializers
        assertThat(serDeserializerRegistry).isNotNull();

        final PersonId id = new PersonId();
        final PersonName name = new PersonName("Peter Parker");
        final PersonCreatedEvent event = personCreatedEvent(id, name);
        final CommonEvent commonEvent = commonEvent(event);

        final String expectedJson = Utils4J.replaceVars("""
                {
                  "data": {
                    "event-id": "${event-id}",
                    "event-timestamp": "${event-timestamp}",
                    "aggregate-version": 0,
                    "entity-id-path": "PERSON ${person-id}",
                    "id": "${person-id}",
                    "name": "Peter Parker"
                  },
                  "dataType": "PersonCreatedEvent",
                  "id": "${event-id}"
                }
                """, Map.of(
                "event-timestamp", event.getEventTimestamp().toString(),
                "event-id", event.getEventId().toString(),
                "person-id", event.getId().toString()
        ));
        final String actualJson = jsonbProvider.jsonb().toJson(commonEvent);
        assertThatJson(actualJson).ignoring("data.event-timestamp").isEqualTo(expectedJson);

    }

}
