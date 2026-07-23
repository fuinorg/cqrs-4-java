package org.fuin.cqrs4j.springboot.query.core.view;

import org.fuin.esc.api.Backoff;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProjectionConfig} class.
 */
class ProjectionConfigTest {

    @Test
    void testUnsetPropertiesKeepThePreviousBehaviour() {

        // Constructor binding passes null for everything that is not configured. An application that
        // configures nothing must behave exactly as it did while these were constants.
        final ProjectionConfig testee = ProjectionConfig.defaults();

        assertThat(testee.getBreakerWindowSize()).isEqualTo(4);
        assertThat(testee.getBreakerFailureRatePercent()).isEqualTo(50.0f);
        assertThat(testee.getBreakerInitialWait()).isEqualTo(Duration.ofSeconds(5));
        assertThat(testee.getBreakerBackoffMultiplier()).isEqualTo(2.0);
        assertThat(testee.getBreakerMaxWait()).isEqualTo(Duration.ofMinutes(5));
        assertThat(testee.getLeaseLockTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(testee.getResubscribeBackoff()).isEqualTo(Backoff.DEFAULT);
    }

    @Test
    void testConfiguredValuesAreUsed() {

        // PREPARE & TEST
        final ProjectionConfig testee = new ProjectionConfig(10, 25.0f, Duration.ofSeconds(1), 3.0,
                Duration.ofMinutes(2), Duration.ofMillis(100), Duration.ofSeconds(10), 1.5, 0.25, 7,
                Duration.ofSeconds(9));

        // VERIFY
        assertThat(testee.getBreakerWindowSize()).isEqualTo(10);
        assertThat(testee.getBreakerFailureRatePercent()).isEqualTo(25.0f);
        assertThat(testee.getBreakerInitialWait()).isEqualTo(Duration.ofSeconds(1));
        assertThat(testee.getBreakerBackoffMultiplier()).isEqualTo(3.0);
        assertThat(testee.getBreakerMaxWait()).isEqualTo(Duration.ofMinutes(2));
        assertThat(testee.getLeaseLockTimeout()).isEqualTo(Duration.ofSeconds(9));
        assertThat(testee.getResubscribeBackoff()).isEqualTo(
                new Backoff(Duration.ofMillis(100), Duration.ofSeconds(10), 1.5, 0.25, 7));
    }

    @Test
    void testASinglePropertyCanBeOverriddenOnItsOwn() {

        // Only the breaker window is set; everything else must still come out as its default.
        final ProjectionConfig testee = new ProjectionConfig(9, null, null, null, null, null, null, null,
                null, null, null);

        assertThat(testee.getBreakerWindowSize()).isEqualTo(9);
        assertThat(testee.getBreakerMaxWait()).isEqualTo(Duration.ofMinutes(5));
        assertThat(testee.getResubscribeBackoff()).isEqualTo(Backoff.DEFAULT);
    }

}
