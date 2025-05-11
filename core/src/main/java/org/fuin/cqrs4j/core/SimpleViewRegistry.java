package org.fuin.cqrs4j.core;

import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * Registry that copies information from the view instances.
 */
public class SimpleViewRegistry implements ViewRegistry {

    private final List<Entry> viewClasses;

    /**
     * Constructor with list of views.
     *
     * @param views Views.
     */
    @SuppressWarnings("unchecked")
    public SimpleViewRegistry(final List<View> views) {
        viewClasses = views.stream()
                .map(v -> new Entry((Class<View>)
                        v.getBeanClass(),
                        v.getBeanName(),
                        v.getStreamName(),
                        v.getDisplayName(),
                        v.getCron(),
                        v.getChunkSize(),
                        v.getEventTypes()))
                .toList();
    }

    @Nonnull
    @Override
    public List<Entry> getViews() {
        return viewClasses;
    }

    @Override
    public int size() {
        return viewClasses.size();
    }

    @Override
    public boolean isEmpty() {
        return viewClasses.isEmpty();
    }

}
