package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EventType;

import java.util.Set;

/**
 * Defines a unit that projects events into another representation.
 */
public interface View {

    /**
     * Unique name of the view.
     *
     * @return Name that is unique in this program instance.
     */
    String getName();

    /**
     * Returns the type of events the view is interested in.
     *
     * @return List of events.
     */
    Set<EventType> getEventTypes();


}
