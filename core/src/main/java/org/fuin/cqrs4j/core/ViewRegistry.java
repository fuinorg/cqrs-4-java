package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EventType;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;
import java.util.Set;

/**
 * Contains information about all available views.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface ViewRegistry {

    /**
     * Returns a list of all view classes.
     *
     * @return View classes.
     */
    List<Entry> getViews();

    /**
     * Returns the number of elements in the regsitry.
     *
     * @return Number of view classes.
     */
    int size();

    /**
     * Returns the information if no view classes are available.
     *
     * @return {@literal true} if no view classes are available.
     */
    boolean isEmpty();

    /**
     * Information about the view.
     *
     * @param viewClass   View class.
     * @param beanName    Name used for the bean.
     * @param projectionName Name used for the projection.
     * @param streamName  Name used for the projection stream.
     * @param cron        CRON expression defining how often the view should be updated.
     * @param chunkSize   Number of events (defaults to 100).
     * @param eventTypes  Type of events the view is interested in.
     * @param eventCategories Category names (simple names of marker interfaces) the view is interested in.
     */
    record Entry(Class<View> viewClass,
                 String name,
                 String beanName,
                 String projectionName,
                 String streamName,
                 String cron,
                 int chunkSize,
                 Set<EventType> eventTypes,
                 Set<String> eventCategories) {
    }

}
