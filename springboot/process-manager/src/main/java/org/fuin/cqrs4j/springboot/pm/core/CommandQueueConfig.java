package org.fuin.cqrs4j.springboot.pm.core;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the command queue executor that drains the outbox.
 */
@ThreadSafe
@ConfigurationProperties(CommandQueueConfig.PREFIX)
public class CommandQueueConfig {

    static final String PREFIX = "org.fuin.cqrs4j.pm.cmdqueue";

    /** Default cron expression (every 5 seconds). */
    public static final String DEFAULT_CRON = "*/5 * * * * *";

    /** Default number of commands read from the outbox per run. */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** Default number of delivery attempts before a command is dead-lettered. */
    public static final int DEFAULT_MAX_RETRIES = 5;

    @NotEmpty
    private final String url;

    @NotEmpty
    private final String cron;

    @Min(1)
    private final int batchSize;

    @Min(1)
    private final int maxRetries;

    /**
     * Constructor with all data. Missing values are replaced with sensible defaults.
     *
     * @param url        Base URL of the command endpoint commands are sent to.
     * @param cron       Cron expression defining how often the outbox is drained.
     * @param batchSize  Number of commands read from the outbox per run.
     * @param maxRetries Number of delivery attempts before a command is dead-lettered.
     */
    public CommandQueueConfig(final String url,
                              final String cron,
                              final Integer batchSize,
                              final Integer maxRetries) {
        super();
        this.url = url == null ? "http://localhost:8080" : url;
        this.cron = cron == null ? DEFAULT_CRON : cron;
        this.batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        this.maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
    }

    /**
     * Returns the base URL of the command endpoint.
     *
     * @return Command endpoint base URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the cron expression defining how often the outbox is drained.
     *
     * @return Cron expression.
     */
    public String getCron() {
        return cron;
    }

    /**
     * Returns the number of commands read from the outbox per run.
     *
     * @return Batch size.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Returns the number of delivery attempts before a command is dead-lettered.
     *
     * @return Maximum number of retries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

}
