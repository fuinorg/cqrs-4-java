package org.fuin.cqrs4j.quarkus.pm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProcessTimeoutConfig} class.
 */
class ProcessTimeoutConfigTest {

    @Test
    void testDefaults() {
        final ProcessTimeoutConfig config = new ProcessTimeoutConfig(null, null);
        assertThat(config.getBatchSize()).isEqualTo(ProcessTimeoutConfig.DEFAULT_BATCH_SIZE);
        assertThat(config.getMaxRetries()).isEqualTo(ProcessTimeoutConfig.DEFAULT_MAX_RETRIES);
    }

    @Test
    void testExplicitValues() {
        final ProcessTimeoutConfig config = new ProcessTimeoutConfig(10, 3);
        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getMaxRetries()).isEqualTo(3);
    }

}
