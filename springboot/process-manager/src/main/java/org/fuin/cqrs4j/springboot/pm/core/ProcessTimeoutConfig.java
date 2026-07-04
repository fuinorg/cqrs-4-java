package org.fuin.cqrs4j.springboot.pm.core;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the process-timeout sweeper that detects and repairs stuck process instances.
 */
@ThreadSafe
@ConfigurationProperties(ProcessTimeoutConfig.PREFIX)
public class ProcessTimeoutConfig {

    static final String PREFIX = "org.fuin.cqrs4j.pm.timeout";

    /** Default cron expression (every 5 seconds). */
    public static final String DEFAULT_CRON = "*/5 * * * * *";

    /** Default number of due timeouts handled per run. */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** Default number of handling attempts before a timeout is dead-lettered. */
    public static final int DEFAULT_MAX_RETRIES = 5;

    @NotEmpty
    private final String cron;

    @Min(1)
    private final int batchSize;

    @Min(1)
    private final int maxRetries;

    /**
     * Constructor with all data. Missing values are replaced with sensible defaults.
     *
     * @param cron       Cron expression defining how often due timeouts are swept.
     * @param batchSize  Number of due timeouts handled per run.
     * @param maxRetries Number of handling attempts before a timeout is dead-lettered.
     */
    public ProcessTimeoutConfig(final String cron,
                                final Integer batchSize,
                                final Integer maxRetries) {
        super();
        this.cron = cron == null ? DEFAULT_CRON : cron;
        this.batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        this.maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
    }

    /**
     * Returns the cron expression defining how often due timeouts are swept.
     *
     * @return Cron expression.
     */
    public String getCron() {
        return cron;
    }

    /**
     * Returns the number of due timeouts handled per run.
     *
     * @return Batch size.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Returns the number of handling attempts before a timeout is dead-lettered.
     *
     * @return Maximum number of retries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

}
