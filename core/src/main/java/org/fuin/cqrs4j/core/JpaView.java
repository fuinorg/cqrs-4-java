package org.fuin.cqrs4j.core;

import jakarta.persistence.EntityManager;
import org.fuin.ddd4j.core.Event;

import java.util.List;

/**
 * Defines a unit that projects events read from the event store into another representation.
 * The view is updated regularly by using a scheduler and the result will be stored using JPA.
 */
public interface JpaView extends View {

    /**
     * Returns the CRON expression defining how often the view should be updated.
     *
     * @return Spring Quartz CRON expression
     */
    String getCron();


    /**
     * Number of events to read and handle in one transaction.
     *
     * @return Number of events (defaults to 100).
     */
    default int getChunkSize() {
        return 100;
    }


    /**
     * Events to handle by the view.
     *
     * @param em     Entity manager to use.
     * @param events Events used to update the view.
     */
    void handleEvents(EntityManager em, List<Event> events);

}
