package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Configuration for the command queue executor that drains the outbox. The cron expression is read directly on
 * the {@link CommandQueueExecutor}'s {@code @Scheduled} annotation; this class holds the remaining values.
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

    /** Key for the time to wait for a connection to the command endpoint (milliseconds). */
    public static final String KEY_CONNECT_TIMEOUT = PREFIX + ".connectTimeout";

    /** Key for the time to wait for a response from the command endpoint (milliseconds). */
    public static final String KEY_REQUEST_TIMEOUT = PREFIX + ".requestTimeout";

    /** Key for the time the delivery circuit breaker stays open (milliseconds). */
    public static final String KEY_BREAKER_DELAY = PREFIX + ".breaker.delay";

    /** Key for the number of deliveries judged before the breaker may open. */
    public static final String KEY_BREAKER_REQUEST_VOLUME_THRESHOLD = PREFIX + ".breaker.requestVolumeThreshold";

    /** Key for the share of failed deliveries that opens the breaker. */
    public static final String KEY_BREAKER_FAILURE_RATIO = PREFIX + ".breaker.failureRatio";

    /** Default command endpoint base URL. */
    public static final String DEFAULT_URL = "http://localhost:8080";

    /** Default cron expression (every 5 seconds). */
    public static final String DEFAULT_CRON = "*/5 * * * * *";

    /** Default number of commands read from the outbox per run. */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** Default number of delivery attempts before a command is dead-lettered. */
    public static final int DEFAULT_MAX_RETRIES = 5;

    /**
     * Defaults as text, because an annotation value has to be a compile time constant. All new settings
     * MUST have a default: without one every existing application fails to start until it declares them.
     */
    static final String DEFAULT_CONNECT_TIMEOUT_STR = "5000";

    static final String DEFAULT_REQUEST_TIMEOUT_STR = "5000";

    static final String DEFAULT_BREAKER_DELAY_STR = "30000";

    static final String DEFAULT_BREAKER_REQUEST_VOLUME_THRESHOLD_STR = "4";

    static final String DEFAULT_BREAKER_FAILURE_RATIO_STR = "0.5";

    /** Default time to wait for a connection (milliseconds). */
    public static final int DEFAULT_CONNECT_TIMEOUT = Integer.parseInt(DEFAULT_CONNECT_TIMEOUT_STR);

    /** Default time to wait for a response (milliseconds). */
    public static final int DEFAULT_REQUEST_TIMEOUT = Integer.parseInt(DEFAULT_REQUEST_TIMEOUT_STR);

    /** Default time the delivery circuit breaker stays open (milliseconds). */
    public static final int DEFAULT_BREAKER_DELAY = Integer.parseInt(DEFAULT_BREAKER_DELAY_STR);

    /** Default number of deliveries judged before the breaker may open. */
    public static final int DEFAULT_BREAKER_REQUEST_VOLUME_THRESHOLD =
            Integer.parseInt(DEFAULT_BREAKER_REQUEST_VOLUME_THRESHOLD_STR);

    /** Default share of failed deliveries that opens the breaker. */
    public static final double DEFAULT_BREAKER_FAILURE_RATIO =
            Double.parseDouble(DEFAULT_BREAKER_FAILURE_RATIO_STR);

    @NotEmpty
    private final String url;

    @Min(1)
    private final int batchSize;

    @Min(1)
    private final int maxRetries;

    @Min(1)
    private final int connectTimeout;

    @Min(1)
    private final int requestTimeout;

    @Min(1)
    private final int breakerDelay;

    @Min(1)
    private final int breakerRequestVolumeThreshold;

    private final double breakerFailureRatio;

    /**
     * Constructor with all data. Missing values are replaced with sensible defaults.
     *
     * @param url        Base URL of the command endpoint commands are sent to.
     * @param batchSize  Number of commands read from the outbox per run.
     * @param maxRetries Number of delivery attempts before a command is dead-lettered.
     * @param connectTimeout Time to wait for a connection in milliseconds.
     * @param requestTimeout Time to wait for a response in milliseconds.
     */
    public CommandQueueConfig(@ConfigProperty(name = KEY_URL, defaultValue = DEFAULT_URL) final String url,
                              @ConfigProperty(name = KEY_BATCH_SIZE) final Integer batchSize,
                              @ConfigProperty(name = KEY_MAX_RETRIES) final Integer maxRetries,
                              @ConfigProperty(name = KEY_CONNECT_TIMEOUT, defaultValue = DEFAULT_CONNECT_TIMEOUT_STR) final Integer connectTimeout,
                              @ConfigProperty(name = KEY_REQUEST_TIMEOUT, defaultValue = DEFAULT_REQUEST_TIMEOUT_STR) final Integer requestTimeout,
                              @ConfigProperty(name = KEY_BREAKER_DELAY, defaultValue = DEFAULT_BREAKER_DELAY_STR) final Integer breakerDelay,
                              @ConfigProperty(name = KEY_BREAKER_REQUEST_VOLUME_THRESHOLD, defaultValue = DEFAULT_BREAKER_REQUEST_VOLUME_THRESHOLD_STR) final Integer breakerRequestVolumeThreshold,
                              @ConfigProperty(name = KEY_BREAKER_FAILURE_RATIO, defaultValue = DEFAULT_BREAKER_FAILURE_RATIO_STR) final Double breakerFailureRatio) {
        super();
        this.url = url == null ? DEFAULT_URL : url;
        this.batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        this.maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
        this.connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        this.requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        this.breakerDelay = breakerDelay == null ? DEFAULT_BREAKER_DELAY : breakerDelay;
        this.breakerRequestVolumeThreshold = breakerRequestVolumeThreshold == null
                ? DEFAULT_BREAKER_REQUEST_VOLUME_THRESHOLD : breakerRequestVolumeThreshold;
        this.breakerFailureRatio = breakerFailureRatio == null
                ? DEFAULT_BREAKER_FAILURE_RATIO : breakerFailureRatio;
    }

    /**
     * Returns the time the delivery circuit breaker stays open.
     *
     * @return Breaker delay in milliseconds.
     */
    public int getBreakerDelay() {
        return breakerDelay;
    }

    /**
     * Returns the number of deliveries judged before the breaker may open.
     *
     * @return Request volume threshold.
     */
    public int getBreakerRequestVolumeThreshold() {
        return breakerRequestVolumeThreshold;
    }

    /**
     * Returns the share of failed deliveries that opens the breaker.
     *
     * @return Failure ratio.
     */
    public double getBreakerFailureRatio() {
        return breakerFailureRatio;
    }

    /**
     * Returns the time to wait for a connection to the command endpoint.
     *
     * @return Connect timeout in milliseconds.
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Returns the time to wait for a response from the command endpoint.
     *
     * @return Request timeout in milliseconds.
     */
    public int getRequestTimeout() {
        return requestTimeout;
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
