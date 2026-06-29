package org.fuin.cqrs4j.springboot.test.pm;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.ProcessManagerView;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.springboot.pm.core.CommandOutboxService;
import org.fuin.cqrs4j.springboot.test.model.PersonCreatedEvent;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Sample transactional-outbox process manager driven as a normal {@link View} by the event store: on
 * a {@link PersonCreatedEvent} it records its own state ({@link SampleProcessManagerState}) and sends a
 * {@link SampleNotifyCommand} - both within the same transaction (state + outbox row).
 */
public class SampleProcessManagerView implements ProcessManagerView {

    private static final Logger LOG = LoggerFactory.getLogger(SampleProcessManagerView.class);

    /** Unique name of the view. */
    public static final String NAME = "SampleProcessManager";

    /** Bean name of the view. */
    public static final String BEAN_NAME = NAME + "View";

    /**
     * Test hook: person names for which {@link #handleEvents(List)} throws <em>after</em> recording its
     * state and command, to simulate a failure while reacting. Because handling runs inside the view
     * engine's transaction (which also advances the projection checkpoint), the rollback discards the
     * state and the outbox row and the event is read again on the next poll.
     */
    public static final Set<String> FAIL_HANDLING_FOR_NAMES = new CopyOnWriteArraySet<>();

    private final CommandOutboxService outboxService;

    private final EntityManager em;

    /**
     * Constructor with mandatory data.
     *
     * @param outboxService Service used to enqueue commands.
     * @param em            Entity manager used to persist the process manager state.
     */
    public SampleProcessManagerView(final CommandOutboxService outboxService, final EntityManager em) {
        this.outboxService = outboxService;
        this.em = em;
    }

    @Override
    public CommandOutboxService getCommandOutboxService() {
        return outboxService;
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
        return "* * * * * *";
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
        // Idempotent: the view engine may re-deliver an event, so only react once.
        if (em.find(SampleProcessManagerState.class, event.getId().asBaseType()) == null) {
            final String name = event.getName().asBaseType();
            LOG.info("Process manager reacts to {}", event);
            em.persist(new SampleProcessManagerState(event.getId().asBaseType(), name));
            send(new SampleNotifyCommand("Welcome " + name));
            if (FAIL_HANDLING_FOR_NAMES.contains(name)) {
                throw new IllegalStateException("Simulated processing failure for: " + name);
            }
        }
    }

}
