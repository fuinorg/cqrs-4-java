package org.fuin.cqrs4j.quarkus.pm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CommandQueueConfig} class.
 */
class CommandQueueConfigTest {

    @Test
    void testDefaults() {
        final CommandQueueConfig config = new CommandQueueConfig(null, null, null, null, null, null, null, null);
        assertThat(config.getUrl()).isEqualTo(CommandQueueConfig.DEFAULT_URL);
        assertThat(config.getBatchSize()).isEqualTo(CommandQueueConfig.DEFAULT_BATCH_SIZE);
        assertThat(config.getMaxRetries()).isEqualTo(CommandQueueConfig.DEFAULT_MAX_RETRIES);
        assertThat(config.getConnectTimeout()).isEqualTo(CommandQueueConfig.DEFAULT_CONNECT_TIMEOUT);
        assertThat(config.getRequestTimeout()).isEqualTo(CommandQueueConfig.DEFAULT_REQUEST_TIMEOUT);
        assertThat(config.getBreakerDelay()).isEqualTo(CommandQueueConfig.DEFAULT_BREAKER_DELAY);
        assertThat(config.getBreakerRequestVolumeThreshold())
                .isEqualTo(CommandQueueConfig.DEFAULT_BREAKER_REQUEST_VOLUME_THRESHOLD);
        assertThat(config.getBreakerFailureRatio()).isEqualTo(CommandQueueConfig.DEFAULT_BREAKER_FAILURE_RATIO);
    }

    @Test
    void testExplicitValues() {
        final CommandQueueConfig config = new CommandQueueConfig("https://cmd.example.org", 10, 3,
                1000, 2000, 15000, 8, 0.75);
        assertThat(config.getUrl()).isEqualTo("https://cmd.example.org");
        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.getConnectTimeout()).isEqualTo(1000);
        assertThat(config.getRequestTimeout()).isEqualTo(2000);
        assertThat(config.getBreakerDelay()).isEqualTo(15000);
        assertThat(config.getBreakerRequestVolumeThreshold()).isEqualTo(8);
        assertThat(config.getBreakerFailureRatio()).isEqualTo(0.75);
    }

}
