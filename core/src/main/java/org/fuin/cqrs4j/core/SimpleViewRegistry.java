package org.fuin.cqrs4j.core;

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
                        v.getName(),
                        v.getBeanName(),
                        v.getProjectionName(),
                        v.getStreamName(),
                        v.getCron(),
                        v.getChunkSize(),
                        v.getEventTypes()))
                .toList();
    }

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
