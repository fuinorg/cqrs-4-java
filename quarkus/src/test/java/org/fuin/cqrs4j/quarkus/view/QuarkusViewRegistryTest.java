package org.fuin.cqrs4j.quarkus.view;

import io.quarkus.arc.All;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.quarkus.base.TestView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link QuarkusViewRegistry} class.
 */
@QuarkusComponentTest
class QuarkusViewRegistryTest {

    @Inject
    BeanManager beanManager;

    @Inject
    TestView testView;

    @Inject
    QuarkusViewRegistry testee;

    @Test
    void testGetViews() {
        assertThat(testee.getViews()).isNotEmpty();
        assertThat(testee.size()).isEqualTo(1);
        assertThat(testee.isEmpty()).isFalse();
    }

}