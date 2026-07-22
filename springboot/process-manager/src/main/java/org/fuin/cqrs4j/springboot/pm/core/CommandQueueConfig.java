package org.fuin.cqrs4j.springboot.pm.core;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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

    /** Default time to wait for a connection to the command endpoint. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Default time to wait for a response from the command endpoint. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** Default number of deliveries judged before the breaker may open. */
    public static final int DEFAULT_BREAKER_WINDOW_SIZE = 4;

    /** Default percentage of failed deliveries that opens the breaker. */
    public static final float DEFAULT_BREAKER_FAILURE_RATE_PERCENT = 50.0f;

    /** Default wait before the first probe after the breaker opened. */
    public static final Duration DEFAULT_BREAKER_INITIAL_WAIT = Duration.ofSeconds(5);

    /** Default upper bound for the wait between probes. */
    public static final Duration DEFAULT_BREAKER_MAX_WAIT = Duration.ofMinutes(5);

    @NotEmpty
    private final String url;

    @NotEmpty
    private final String cron;

    @Min(1)
    private final int batchSize;

    @Min(1)
    private final int maxRetries;

    private final Duration connectTimeout;

    private final Duration requestTimeout;

    @Min(1)
    private final int breakerWindowSize;

    private final float breakerFailureRatePercent;

    private final Duration breakerInitialWait;

    private final Duration breakerMaxWait;

    /**
     * Constructor with all data. Missing values are replaced with sensible defaults.
     *
     * @param url        Base URL of the command endpoint commands are sent to.
     * @param cron       Cron expression defining how often the outbox is drained.
     * @param batchSize  Number of commands read from the outbox per run.
     * @param maxRetries Number of delivery attempts before a command is dead-lettered.
     * @param connectTimeout Time to wait for a connection to the command endpoint.
     * @param requestTimeout Time to wait for a response from the command endpoint.
     * @param breakerWindowSize Number of deliveries judged before the breaker may open.
     * @param breakerFailureRatePercent Percentage of failed deliveries that opens the breaker.
     * @param breakerInitialWait Wait before the first probe after the breaker opened.
     * @param breakerMaxWait Upper bound for the wait between probes.
     */
    public CommandQueueConfig(final String url,
                              final String cron,
                              final Integer batchSize,
                              final Integer maxRetries,
                              final Duration connectTimeout,
                              final Duration requestTimeout,
                              final Integer breakerWindowSize,
                              final Float breakerFailureRatePercent,
                              final Duration breakerInitialWait,
                              final Duration breakerMaxWait) {
        super();
        this.url = url == null ? "http://localhost:8080" : url;
        this.cron = cron == null ? DEFAULT_CRON : cron;
        this.batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        this.maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
        this.connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        this.requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        this.breakerWindowSize = breakerWindowSize == null ? DEFAULT_BREAKER_WINDOW_SIZE : breakerWindowSize;
        this.breakerFailureRatePercent = breakerFailureRatePercent == null
                ? DEFAULT_BREAKER_FAILURE_RATE_PERCENT : breakerFailureRatePercent;
        this.breakerInitialWait = breakerInitialWait == null ? DEFAULT_BREAKER_INITIAL_WAIT : breakerInitialWait;
        this.breakerMaxWait = breakerMaxWait == null ? DEFAULT_BREAKER_MAX_WAIT : breakerMaxWait;
    }

    /**
     * Returns the time to wait for a connection to the command endpoint.
     *
     * @return Connect timeout.
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Returns the time to wait for a response from the command endpoint.
     *
     * @return Request timeout.
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Returns the number of deliveries judged before the breaker may open.
     *
     * @return Sliding window size.
     */
    public int getBreakerWindowSize() {
        return breakerWindowSize;
    }

    /**
     * Returns the percentage of failed deliveries that opens the breaker.
     *
     * @return Failure rate threshold in percent.
     */
    public float getBreakerFailureRatePercent() {
        return breakerFailureRatePercent;
    }

    /**
     * Returns the wait before the first probe after the breaker opened.
     *
     * @return Initial wait.
     */
    public Duration getBreakerInitialWait() {
        return breakerInitialWait;
    }

    /**
     * Returns the upper bound for the wait between probes.
     *
     * @return Maximum wait.
     */
    public Duration getBreakerMaxWait() {
        return breakerMaxWait;
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
