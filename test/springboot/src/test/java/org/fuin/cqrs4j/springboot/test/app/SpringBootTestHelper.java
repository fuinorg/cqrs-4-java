package org.fuin.cqrs4j.springboot.test.app;

import org.fuin.cqrs4j.springboot.test.model.PersonCreatedEvent;
import org.fuin.cqrs4j.springboot.test.model.PersonId;
import org.fuin.cqrs4j.springboot.test.model.PersonName;
import org.fuin.ddd4j.core.AggregateVersion;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.SimpleCommonEvent;
import org.fuin.esc.api.TypeName;

import java.time.ZonedDateTime;

/**
 * Helper functions for the test modules.
 */
public final class SpringBootTestHelper {

    private SpringBootTestHelper() {
        throw new UnsupportedOperationException("Cannot instantiate utility class");
    }

    /**
     * Creates a {@link PersonCreatedEvent} packed into a {@link CommonEvent}.
     *
     * @param id   Unique person identifier.
     * @param name Name of the person.
     * @return Event to store.
     */
    public static CommonEvent createPersonCreatedEvent(PersonId id, PersonName name) {
        final org.fuin.esc.api.EventId eventId = new org.fuin.esc.api.EventId();
        final PersonCreatedEvent event = PersonCreatedEvent.builder()
                .aggregateVersion(AggregateVersion.valueOf(0))
                .entityIdPath(id)
                .eventId(new org.fuin.ddd4j.core.EventId(eventId.asBaseType()))
                .id(id)
                .name(name)
                .timestamp(ZonedDateTime.now())
                .build();
        return new SimpleCommonEvent(
                eventId,
                new TypeName(PersonCreatedEvent.TYPE.asBaseType()),
                event,
                null);
    }

}
