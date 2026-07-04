package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.Min;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Configuration for the process-timeout sweeper that detects and repairs stuck process instances. The cron
 * expression is read directly on the {@link QuarkusProcessTimeoutSweeper}'s {@code @Scheduled} annotation; this
 * class holds the remaining values.
 */
@ThreadSafe
@ApplicationScoped
public class ProcessTimeoutConfig {

    static final String PREFIX = "org.fuin.cqrs4j.pm.timeout";

    /** Key for the drain batch size. */
    public static final String KEY_BATCH_SIZE = PREFIX + ".batchSize";

    /** Key for the maximum number of handling attempts. */
    public static final String KEY_MAX_RETRIES = PREFIX + ".maxRetries";

    /** Default cron expression (every 5 seconds). */
    public static final String DEFAULT_CRON = "*/5 * * * * *";

    /** Default number of due timeouts handled per run. */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** Default number of handling attempts before a timeout is dead-lettered. */
    public static final int DEFAULT_MAX_RETRIES = 5;

    @Min(1)
    private final int batchSize;

    @Min(1)
    private final int maxRetries;

    /**
     * Constructor with all data. Missing values are replaced with sensible defaults.
     *
     * @param batchSize  Number of due timeouts handled per run.
     * @param maxRetries Number of handling attempts before a timeout is dead-lettered.
     */
    public ProcessTimeoutConfig(@ConfigProperty(name = KEY_BATCH_SIZE) final Integer batchSize,
                                @ConfigProperty(name = KEY_MAX_RETRIES) final Integer maxRetries) {
        super();
        this.batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        this.maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
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
