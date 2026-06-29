package org.fuin.cqrs4j.springboot.pm.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CommandQueueConfig} class.
 */
class CommandQueueConfigTest {

    @Test
    void testDefaults() {
        final CommandQueueConfig config = new CommandQueueConfig(null, null, null, null);
        assertThat(config.getUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.getCron()).isEqualTo(CommandQueueConfig.DEFAULT_CRON);
        assertThat(config.getBatchSize()).isEqualTo(CommandQueueConfig.DEFAULT_BATCH_SIZE);
        assertThat(config.getMaxRetries()).isEqualTo(CommandQueueConfig.DEFAULT_MAX_RETRIES);
    }

    @Test
    void testExplicitValues() {
        final CommandQueueConfig config = new CommandQueueConfig("https://cmd.example.org", "0 0 * * * *", 10, 3);
        assertThat(config.getUrl()).isEqualTo("https://cmd.example.org");
        assertThat(config.getCron()).isEqualTo("0 0 * * * *");
        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getMaxRetries()).isEqualTo(3);
    }

}
