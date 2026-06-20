package org.fuin.cqrs4j.springboot.query.core.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link SpringViewRegistry} class.
 */
@SpringBootTest(classes = {SpringViewRegistry.class, TestView.class})
class SpringViewRegistryTest {

    @Autowired
    private SpringViewRegistry testee;

    @Test
    void testGetViews() {
        assertThat(testee.getViews()).isNotEmpty();
        assertThat(testee.size()).isEqualTo(1);
        assertThat(testee.isEmpty()).isFalse();
    }

}