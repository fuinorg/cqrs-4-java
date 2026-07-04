package org.fuin.cqrs4j.springboot.pm.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ProcessTimeoutConfig} class.
 */
class ProcessTimeoutConfigTest {

    @Test
    void testDefaults() {
        final ProcessTimeoutConfig config = new ProcessTimeoutConfig(null, null, null);
        assertThat(config.getCron()).isEqualTo(ProcessTimeoutConfig.DEFAULT_CRON);
        assertThat(config.getBatchSize()).isEqualTo(ProcessTimeoutConfig.DEFAULT_BATCH_SIZE);
        assertThat(config.getMaxRetries()).isEqualTo(ProcessTimeoutConfig.DEFAULT_MAX_RETRIES);
    }

    @Test
    void testExplicitValues() {
        final ProcessTimeoutConfig config = new ProcessTimeoutConfig("0 0 * * * *", 10, 3);
        assertThat(config.getCron()).isEqualTo("0 0 * * * *");
        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getMaxRetries()).isEqualTo(3);
    }

}
