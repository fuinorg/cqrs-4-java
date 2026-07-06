package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EventType;
import org.fuin.objects4j.common.ThreadSafe;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.Adler32;

/**
 * CQRS related helper functions.
 */
@ThreadSafe
public final class CqrsUtils {

    private CqrsUtils() {
        throw new UnsupportedOperationException("Utility classes cannot be instantiated");
    }

    /**
     * Creates an Adler32 checksum based on event type names.
     *
     * @param eventTypes Types to calculate a checksum for.
     * @return Checksum based on all names.
     */
    public static long calculateAdler32Checksum(final Collection<EventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes cannot be null or empty");
        }
        final List<EventType> sortedList = new ArrayList<>(eventTypes);
        Collections.sort(sortedList);
        final Adler32 checksum = new Adler32();
        for (final EventType eventType : sortedList) {
            checksum.update(eventType.asBaseType().getBytes(StandardCharsets.US_ASCII));
        }
        return checksum.getValue();
    }

    /**
     * Calculates an Adler32 checksum over a view's selection (event types <em>and</em> categories) so that
     * two views with a different selection get distinct projection stream identities. Either set may be empty
     * but not both.
     *
     * @param eventTypes Event types the view selects (may be empty).
     * @param categories Category names the view selects (may be empty).
     *
     * @return Checksum.
     */
    public static long calculateAdler32Checksum(final Collection<EventType> eventTypes,
                                                final Collection<String> categories) {
        final boolean noTypes = eventTypes == null || eventTypes.isEmpty();
        final boolean noCategories = categories == null || categories.isEmpty();
        if (noTypes && noCategories) {
            throw new IllegalArgumentException("eventTypes and categories cannot both be null or empty");
        }
        final Adler32 checksum = new Adler32();
        if (!noTypes) {
            final List<String> sortedTypes = new ArrayList<>();
            for (final EventType eventType : eventTypes) {
                sortedTypes.add(eventType.asBaseType());
            }
            Collections.sort(sortedTypes);
            for (final String type : sortedTypes) {
                checksum.update(type.getBytes(StandardCharsets.US_ASCII));
            }
        }
        // Separator so that {types=[A], categories=[]} and {types=[], categories=[A]} differ.
        checksum.update('|');
        if (!noCategories) {
            final List<String> sortedCategories = new ArrayList<>(categories);
            Collections.sort(sortedCategories);
            for (final String category : sortedCategories) {
                checksum.update(category.getBytes(StandardCharsets.US_ASCII));
            }
        }
        return checksum.getValue();
    }

}
