package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Configuration for the command queue executor that drains the outbox. The cron expression is read directly on
 * the {@link QuarkusCommandQueueExecutor}'s {@code @Scheduled} annotation; this class holds the remaining values.
 */
@ThreadSafe
@ApplicationScoped
public class CommandQueueConfig {

    static final String PREFIX = "org.fuin.cqrs4j.pm.cmdqueue";

    /** Key for the command endpoint base URL. */
    public static final String KEY_URL = PREFIX + ".url";

    /** Key for the drain batch size. */
    public static final String KEY_BATCH_SIZE = PREFIX + ".batchSize";

    /** Key for the maximum number of delivery attempts. */
    public static final String KEY_MAX_RETRIES = PREFIX + ".maxRetries";

    /** Default command endpoint base URL. */
    public static final String DEFAULT_URL = "http://localhost:8080";

    /** Default cron expression (every 5 seconds). */
    public static final String DEFAULT_CRON = "*/5 * * * * *";

    /** Default number of commands read from the outbox per run. */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** Default number of delivery attempts before a command is dead-lettered. */
    public static final int DEFAULT_MAX_RETRIES = 5;

    @NotEmpty
    private final String url;

    @Min(1)
    private final int batchSize;

    @Min(1)
    private final int maxRetries;

    /**
     * Constructor with all data. Missing values are replaced with sensible defaults.
     *
     * @param url        Base URL of the command endpoint commands are sent to.
     * @param batchSize  Number of commands read from the outbox per run.
     * @param maxRetries Number of delivery attempts before a command is dead-lettered.
     */
    public CommandQueueConfig(@ConfigProperty(name = KEY_URL, defaultValue = DEFAULT_URL) final String url,
                              @ConfigProperty(name = KEY_BATCH_SIZE) final Integer batchSize,
                              @ConfigProperty(name = KEY_MAX_RETRIES) final Integer maxRetries) {
        super();
        this.url = url == null ? DEFAULT_URL : url;
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
