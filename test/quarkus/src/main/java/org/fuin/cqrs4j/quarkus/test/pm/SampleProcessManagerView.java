package org.fuin.cqrs4j.quarkus.test.pm;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.CommandOutbox;
import org.fuin.cqrs4j.core.ProcessManagerView;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.quarkus.pm.QuarkusCommandOutboxService;
import org.fuin.cqrs4j.quarkus.test.cmd.SampleGreetCommand;
import org.fuin.cqrs4j.quarkus.test.model.PersonCreatedEvent;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;
import java.util.Set;

/**
 * Sample transactional-outbox process manager driven as a normal {@link View} by the event store: on a
 * {@link PersonCreatedEvent} it records its own state ({@link SampleProcessManagerState}) and sends a
 * {@link SampleGreetCommand} - both within the same transaction (state + outbox row). The command is later drained
 * to the {@code /cmd/{type}} receiver by the process-manager outbox.
 */
@ThreadSafe
@Dependent
@Named(SampleProcessManagerView.BEAN_NAME)
public class SampleProcessManagerView implements ProcessManagerView {

    /** Unique view name. */
    public static final String NAME = "SampleProcessManager";

    /** Bean name. */
    public static final String BEAN_NAME = NAME + "View";

    private final QuarkusCommandOutboxService outboxService;

    private final EntityManager em;

    /**
     * Constructor with mandatory dependencies.
     *
     * @param outboxService Service used to enqueue commands.
     * @param em            Entity manager used to persist the process manager state.
     */
    @Inject
    public SampleProcessManagerView(final QuarkusCommandOutboxService outboxService, final EntityManager em) {
        this.outboxService = outboxService;
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
        return SampleProcessManagerView.class;
    }

    @Override
    public Set<EventType> getEventTypes() {
        return Set.of(PersonCreatedEvent.TYPE);
    }

    @Override
    public String getCron() {
        // Every second
        return "* * * * * ?";
    }

    @Override
    public CommandOutbox getCommandOutboxService() {
        return outboxService;
    }

    @Override
    public void handleEvents(final List<Event> events) {
        for (final Event event : events) {
            if (event instanceof PersonCreatedEvent ev) {
                handlePersonCreated(ev);
            } else {
                throw new IllegalStateException("Cannot handle event: " + event);
            }
        }
    }

    private void handlePersonCreated(final PersonCreatedEvent event) {
        // Idempotent: only react (and send) once per person.
        if (em.find(SampleProcessManagerState.class, event.getId().asBaseType()) == null) {
            final String name = event.getName().asBaseType();
            em.persist(new SampleProcessManagerState(event.getId().asBaseType(), name));
            send(new SampleGreetCommand("Welcome " + name));
        }
    }

}
