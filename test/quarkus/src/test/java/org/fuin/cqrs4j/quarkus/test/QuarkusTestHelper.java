package org.fuin.cqrs4j.quarkus.test;

import org.fuin.cqrs4j.quarkus.test.model.PersonCreatedEvent;
import org.fuin.cqrs4j.quarkus.test.model.PersonId;
import org.fuin.cqrs4j.quarkus.test.model.PersonName;
import org.fuin.ddd4j.core.AggregateVersion;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.SimpleCommonEvent;
import org.fuin.esc.api.TypeName;

import java.time.ZonedDateTime;

/**
 * Helper functions for the test modules.
 */
public final class QuarkusTestHelper {

    private QuarkusTestHelper() {
        throw new UnsupportedOperationException("Cannot instantiate utility class");
    }

    /**
     * Creates a {@link PersonCreatedEvent}.
     *
     * @param id   Unique person identifier.
     * @param name Name of the person.
     * @return Event to store.
     */
    public static PersonCreatedEvent personCreatedEvent(PersonId id, PersonName name) {
        return PersonCreatedEvent.builder()
                .aggregateVersion(AggregateVersion.valueOf(0))
                .entityIdPath(id)
                .eventId(new org.fuin.ddd4j.core.EventId())
                .id(id)
                .name(name)
                .timestamp(ZonedDateTime.now())
                .build();
    }

    /**
     * Creates a {@link CommonEvent} with a {@link PersonCreatedEvent} inside.
     *
     * @param event Event.
     * @return Event to store.
     */
    public static CommonEvent commonEvent(PersonCreatedEvent event) {
        return new SimpleCommonEvent(
                new org.fuin.esc.api.EventId(event.getEventId().asString()),
                new TypeName(PersonCreatedEvent.TYPE.asBaseType()),
                event);
    }

}
