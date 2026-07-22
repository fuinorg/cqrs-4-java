package org.fuin.cqrs4j.springboot.pm.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CommandQueueConfig} class.
 */
class CommandQueueConfigTest {

    @Test
    void testDefaults() {
        final CommandQueueConfig config = new CommandQueueConfig(null, null, null, null, null, null, null, null, null, null);
        assertThat(config.getUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.getCron()).isEqualTo(CommandQueueConfig.DEFAULT_CRON);
        assertThat(config.getBatchSize()).isEqualTo(CommandQueueConfig.DEFAULT_BATCH_SIZE);
        assertThat(config.getMaxRetries()).isEqualTo(CommandQueueConfig.DEFAULT_MAX_RETRIES);
        assertThat(config.getConnectTimeout()).isEqualTo(CommandQueueConfig.DEFAULT_CONNECT_TIMEOUT);
        assertThat(config.getRequestTimeout()).isEqualTo(CommandQueueConfig.DEFAULT_REQUEST_TIMEOUT);
        assertThat(config.getBreakerWindowSize()).isEqualTo(CommandQueueConfig.DEFAULT_BREAKER_WINDOW_SIZE);
        assertThat(config.getBreakerFailureRatePercent())
                .isEqualTo(CommandQueueConfig.DEFAULT_BREAKER_FAILURE_RATE_PERCENT);
        assertThat(config.getBreakerInitialWait()).isEqualTo(CommandQueueConfig.DEFAULT_BREAKER_INITIAL_WAIT);
        assertThat(config.getBreakerMaxWait()).isEqualTo(CommandQueueConfig.DEFAULT_BREAKER_MAX_WAIT);
    }

    @Test
    void testExplicitValues() {
        final CommandQueueConfig config = new CommandQueueConfig("https://cmd.example.org", "0 0 * * * *", 10, 3,
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2), 8, 75.0f,
                java.time.Duration.ofSeconds(3), java.time.Duration.ofMinutes(1));
        assertThat(config.getUrl()).isEqualTo("https://cmd.example.org");
        assertThat(config.getCron()).isEqualTo("0 0 * * * *");
        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.getConnectTimeout()).isEqualTo(java.time.Duration.ofSeconds(1));
        assertThat(config.getRequestTimeout()).isEqualTo(java.time.Duration.ofSeconds(2));
        assertThat(config.getBreakerWindowSize()).isEqualTo(8);
        assertThat(config.getBreakerFailureRatePercent()).isEqualTo(75.0f);
        assertThat(config.getBreakerInitialWait()).isEqualTo(java.time.Duration.ofSeconds(3));
        assertThat(config.getBreakerMaxWait()).isEqualTo(java.time.Duration.ofMinutes(1));
    }

}
