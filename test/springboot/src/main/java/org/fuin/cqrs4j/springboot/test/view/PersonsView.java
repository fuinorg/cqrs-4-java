package org.fuin.cqrs4j.springboot.test.view;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.JpaView;
import org.fuin.cqrs4j.springboot.test.model.PersonCreatedEvent;
import org.fuin.cqrs4j.springboot.test.model.PersonEntity;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class PersonsView implements JpaView {

    private static final Logger LOG = LoggerFactory.getLogger(PersonsView.class);

    @Override
    public String getName() {
        return "persons-view";
    }

    @Override
    public Set<EventType> getEventTypes() {
        return Set.of(PersonCreatedEvent.TYPE);
    }

    @Override
    public String getCron() {
        // Every second
        return "* * * * * *";
    }

    @Override
    public void handleEvents(final EntityManager em, final List<Event> events) {
        for (final Event event : events) {
            if (event instanceof PersonCreatedEvent ev) {
                handlePersonCreatedEvent(em, ev);
            } else {
                throw new IllegalStateException("Cannot handle event: " + event);
            }
        }
    }

    private void handlePersonCreatedEvent(final EntityManager em, final PersonCreatedEvent event) {
        LOG.info("Handle {}: {}", event.getClass().getSimpleName(), event);
        final PersonEntity entity = em.find(PersonEntity.class, event.getId().asBaseType());
        if (entity == null) {
            em.persist(new PersonEntity(event.getId(), event.getName()));
        } else {
            LOG.info("Ignored {}} because entity already exists: {}", event.getClass().getSimpleName(), event);
        }
    }

}
