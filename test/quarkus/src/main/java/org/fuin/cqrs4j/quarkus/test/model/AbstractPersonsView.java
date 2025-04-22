package org.fuin.cqrs4j.quarkus.test.model;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.View;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles the events required to maintain the persons view.
 */
public abstract class AbstractPersonsView implements View {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractPersonsView.class);

    private final EntityManager em;

    public AbstractPersonsView(EntityManager em) {
        this.em = Objects.requireNonNull(em, "em==null");
    }

    @Override
    public String getName() {
        return "persons-view";
    }

    @Override
    public Set<EventType> getEventTypes() {
        return Set.of(PersonCreatedEvent.TYPE);
    }

    @Override
    public void handleEvents(final List<Event> events) {
        for (final Event event : events) {
            if (event instanceof PersonCreatedEvent ev) {
                handlePersonCreatedEvent(ev);
            } else {
                throw new IllegalStateException("Cannot handle event: " + event);
            }
        }
    }

    private void handlePersonCreatedEvent(final PersonCreatedEvent event) {
        LOG.info("Handle {}: {}", event.getClass().getSimpleName(), event);
        final PersonEntity entity = em.find(PersonEntity.class, event.getId().asBaseType());
        if (entity == null) {
            em.persist(new PersonEntity(event.getId(), event.getName()));
        } else {
            LOG.info("Ignored {}} because entity already exists: {}", event.getClass().getSimpleName(), event);
        }
    }

}