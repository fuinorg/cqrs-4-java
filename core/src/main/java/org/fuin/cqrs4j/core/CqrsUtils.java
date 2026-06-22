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

}
