package org.fuin.cqrs4j.springboot.test.view;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.springboot.test.model.PersonCreatedEvent;
import org.fuin.cqrs4j.springboot.test.model.PersonEntity;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@ThreadSafe
@Component(PersonsView.BEAN_NAME)
@Scope(SCOPE_PROTOTYPE)
public class PersonsView implements View {

    private static final Logger LOG = LoggerFactory.getLogger(PersonsView.class);

    public static final String NAME = "Persons";

    public static final String BEAN_NAME = NAME + "View";

    private final EntityManager em;

    public PersonsView(final EntityManager em) {
        this.em = em;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getBeanName() {
        return BEAN_NAME;
    }

    @Override
    public Class<? extends View> getBeanClass() {
        return PersonsView.class;
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
