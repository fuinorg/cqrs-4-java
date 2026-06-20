package org.fuin.cqrs4j.core;

import org.fuin.cqrs4j.core.ViewRegistry.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link SimpleViewRegistry} class.
 */
class SimpleViewRegistryTest {

    @Test
    void testEmpty() {
        final SimpleViewRegistry testee = new SimpleViewRegistry(List.of());
        assertThat(testee.isEmpty()).isTrue();
        assertThat(testee.size()).isZero();
        assertThat(testee.getViews()).isEmpty();
    }

    @Test
    void testGetViews() {
        final View view = mock(View.class);
        doReturn(View.class).when(view).getBeanClass();
        when(view.getName()).thenReturn("MyView");
        when(view.getBeanName()).thenReturn("myViewBean");
        when(view.getProjectionName()).thenReturn("MyViewProjection");
        when(view.getStreamName()).thenReturn("MyViewStream");
        when(view.getCron()).thenReturn("0 0 * * * *");
        when(view.getChunkSize()).thenReturn(50);
        when(view.getEventTypes()).thenReturn(Set.of());

        final SimpleViewRegistry testee = new SimpleViewRegistry(List.of(view));

        assertThat(testee.isEmpty()).isFalse();
        assertThat(testee.size()).isEqualTo(1);
        assertThat(testee.getViews()).hasSize(1);

        final Entry entry = testee.getViews().get(0);
        assertThat(entry.name()).isEqualTo("MyView");
        assertThat(entry.beanName()).isEqualTo("myViewBean");
        assertThat(entry.projectionName()).isEqualTo("MyViewProjection");
        assertThat(entry.streamName()).isEqualTo("MyViewStream");
        assertThat(entry.cron()).isEqualTo("0 0 * * * *");
        assertThat(entry.chunkSize()).isEqualTo(50);
        assertThat(entry.eventTypes()).isEmpty();
    }

}
