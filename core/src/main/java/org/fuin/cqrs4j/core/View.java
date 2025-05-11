package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;

import java.util.List;
import java.util.Set;

/**
 * Defines a unit that projects events into another representation.
 */
public interface View {

    /**
     * Unique name of the view used for finding the bean.
     *
     * @return Name that is unique in this program instance.
     */
    String getBeanName();

    /**
     * Returns the name of the view class.
     *
     * @return View class name.
     */
    Class<? extends View> getBeanClass();

    /**
     * Unique name that is used for the projection stream in the event store.
     *
     * @return Name that is unique in this program instance.
     */
    default String getStreamName() {
        return getBeanName();
    }


    /**
     * Unique name that is used in the user interface.
     *
     * @return Name that is unique in this program instance.
     */
    default String getDisplayName() {
        return getBeanName();
    }

    /**
     * Returns the type of events the view is interested in.
     *
     * @return Set of events.
     */
    Set<EventType> getEventTypes();


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
     * @param events Events used to update the view.
     */
    void handleEvents(List<Event> events);

}
