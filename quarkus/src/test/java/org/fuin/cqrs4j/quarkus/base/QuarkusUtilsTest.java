package org.fuin.cqrs4j.quarkus.base;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the {@link QuarkusUtils} class.
 */
@QuarkusComponentTest
class QuarkusUtilsTest {

    @Inject
    BeanManager beanManager;

    @Inject
    TestView testView;

    @Test
    void testFindBean() {
        assertThat(QuarkusUtils.findBean(beanManager, "test-view", TestView.class)).isPresent();
    }

}