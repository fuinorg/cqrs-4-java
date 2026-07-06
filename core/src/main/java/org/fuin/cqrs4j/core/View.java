package org.fuin.cqrs4j.core;

import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.ThreadSafetyUndefined;

import java.util.List;
import java.util.Set;

/**
 * Defines a unit that projects events into another representation.
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface View {

    /**
     * Unique name of the view.
     *
     * @return Name that is unique in this program instance.
     */
    String getName();

    /**
     * Returns the name of the view class.
     *
     * @return View class name.
     */
    Class<? extends View> getBeanClass();

    /**
     * Unique name of the view used for finding the bean.
     *
     * @return Name that is unique in this program instance.
     */
    String getBeanName();

    /**
     * Unique name that is used for the projection stream in the event store.
     *
     * @return Name that is unique in this program instance.
     */
    default String getProjectionName() {
        return getName() + "Projection";
    }

    /**
     * Unique name that is used for the projection stream in the event store.
     *
     * @return Name that is unique in this program instance.
     */
    default String getStreamName() {
        return getName() + "Projection";
    }

    /**
     * Returns the type of events the view is interested in.
     *
     * @return Set of events.
     */
    Set<EventType> getEventTypes();

    /**
     * Returns the event categories the view is interested in - marker interfaces the events implement (e.g.
     * {@code GenesisEvent.class}). Unlike {@link #getEventTypes()} this selects events by category, so the
     * view picks up any event carrying one of these categories without knowing its concrete type. The
     * projection selects an event if its type is in {@link #getEventTypes()} <em>or</em> it belongs to one of
     * these categories.
     *
     * @return Set of marker interface classes (empty by default).
     */
    default Set<Class<?>> getEventCategories() {
        return Set.of();
    }


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
     * @param events   Events used to update the view.
     */
    void handleEvents(List<Event> events);

}
