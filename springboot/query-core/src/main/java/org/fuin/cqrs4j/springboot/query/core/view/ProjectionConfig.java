package org.fuin.cqrs4j.springboot.query.core.view;

import jakarta.validation.constraints.Min;
import org.fuin.esc.api.Backoff;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration of the projection catch-up: the circuit breaker that guards it, the schedule for
 * re-establishing a dropped wake-up subscription, and the bound on the lease lock.
 * <p>
 * These were constants until now, which meant an operator could not adjust them without a code change. Every
 * value has a default, so an application that configures nothing behaves exactly as before.
 * <p>
 * Constructor binding passes {@literal null} for a property that is not set, which is what the defaulting
 * below relies on - the same arrangement as {@code CommandQueueConfig} on the process manager side.
 */
@ThreadSafe
@ConfigurationProperties(ProjectionConfig.PREFIX)
public class ProjectionConfig {

    static final String PREFIX = "org.fuin.cqrs4j.projection";

    /** Default number of catch-up attempts judged before the breaker may open. */
    public static final int DEFAULT_BREAKER_WINDOW_SIZE = 4;

    /** Default percentage of failed attempts that opens the breaker. */
    public static final float DEFAULT_BREAKER_FAILURE_RATE_PERCENT = 50.0f;

    /** Default wait before the first probe after the breaker opened. */
    public static final Duration DEFAULT_BREAKER_INITIAL_WAIT = Duration.ofSeconds(5);

    /** Default growth factor applied to the wait after each further failed probe. */
    public static final double DEFAULT_BREAKER_BACKOFF_MULTIPLIER = 2.0;

    /** Default upper bound for the wait between probes. */
    public static final Duration DEFAULT_BREAKER_MAX_WAIT = Duration.ofMinutes(5);

    /** Default delay before a dropped wake-up subscription is re-established. */
    public static final Duration DEFAULT_RESUBSCRIBE_INITIAL_DELAY = Backoff.DEFAULT.initialDelay();

    /** Default upper bound for the re-subscribe delay. */
    public static final Duration DEFAULT_RESUBSCRIBE_MAX_DELAY = Backoff.DEFAULT.maxDelay();

    /** Default growth factor applied to the re-subscribe delay after each further failure. */
    public static final double DEFAULT_RESUBSCRIBE_MULTIPLIER = Backoff.DEFAULT.multiplier();

    /** Default share of the re-subscribe delay that is randomized. */
    public static final double DEFAULT_RESUBSCRIBE_JITTER_FACTOR = Backoff.DEFAULT.jitterFactor();

    /** Default number of re-subscribe attempts; unlimited, because losing the subscription only costs latency. */
    public static final int DEFAULT_RESUBSCRIBE_MAX_ATTEMPTS = Backoff.UNLIMITED_ATTEMPTS;

    /** Default bound for acquiring the projection lease lock. */
    public static final Duration DEFAULT_LEASE_LOCK_TIMEOUT = Duration.ofSeconds(3);

    @Min(1)
    private final int breakerWindowSize;

    private final float breakerFailureRatePercent;

    private final Duration breakerInitialWait;

    private final double breakerBackoffMultiplier;

    private final Duration breakerMaxWait;

    private final Backoff resubscribeBackoff;

    private final Duration leaseLockTimeout;

    /**
     * Constructor binding all properties, defaulting every one that is not configured.
     *
     * @param breakerWindowSize         Attempts judged before the breaker may open.
     * @param breakerFailureRatePercent Percentage of failed attempts that opens the breaker.
     * @param breakerInitialWait        Wait before the first probe after the breaker opened.
     * @param breakerBackoffMultiplier  Growth factor applied after each further failed probe.
     * @param breakerMaxWait            Upper bound for the wait between probes.
     * @param resubscribeInitialDelay   Delay before a dropped subscription is re-established.
     * @param resubscribeMaxDelay       Upper bound for the re-subscribe delay.
     * @param resubscribeMultiplier     Growth factor applied to the re-subscribe delay.
     * @param resubscribeJitterFactor   Share of the re-subscribe delay that is randomized.
     * @param resubscribeMaxAttempts    Maximum re-subscribe attempts, or -1 for unlimited.
     * @param leaseLockTimeout          Bound for acquiring the projection lease lock.
     */
    public ProjectionConfig(@Nullable final Integer breakerWindowSize,
                            @Nullable final Float breakerFailureRatePercent,
                            @Nullable final Duration breakerInitialWait,
                            @Nullable final Double breakerBackoffMultiplier,
                            @Nullable final Duration breakerMaxWait,
                            @Nullable final Duration resubscribeInitialDelay,
                            @Nullable final Duration resubscribeMaxDelay,
                            @Nullable final Double resubscribeMultiplier,
                            @Nullable final Double resubscribeJitterFactor,
                            @Nullable final Integer resubscribeMaxAttempts,
                            @Nullable final Duration leaseLockTimeout) {
        this.breakerWindowSize = breakerWindowSize == null ? DEFAULT_BREAKER_WINDOW_SIZE : breakerWindowSize;
        this.breakerFailureRatePercent = breakerFailureRatePercent == null
                ? DEFAULT_BREAKER_FAILURE_RATE_PERCENT : breakerFailureRatePercent;
        this.breakerInitialWait = breakerInitialWait == null ? DEFAULT_BREAKER_INITIAL_WAIT : breakerInitialWait;
        this.breakerBackoffMultiplier = breakerBackoffMultiplier == null
                ? DEFAULT_BREAKER_BACKOFF_MULTIPLIER : breakerBackoffMultiplier;
        this.breakerMaxWait = breakerMaxWait == null ? DEFAULT_BREAKER_MAX_WAIT : breakerMaxWait;
        this.resubscribeBackoff = new Backoff(
                resubscribeInitialDelay == null ? DEFAULT_RESUBSCRIBE_INITIAL_DELAY : resubscribeInitialDelay,
                resubscribeMaxDelay == null ? DEFAULT_RESUBSCRIBE_MAX_DELAY : resubscribeMaxDelay,
                resubscribeMultiplier == null ? DEFAULT_RESUBSCRIBE_MULTIPLIER : resubscribeMultiplier,
                resubscribeJitterFactor == null ? DEFAULT_RESUBSCRIBE_JITTER_FACTOR : resubscribeJitterFactor,
                resubscribeMaxAttempts == null ? DEFAULT_RESUBSCRIBE_MAX_ATTEMPTS : resubscribeMaxAttempts);
        this.leaseLockTimeout = leaseLockTimeout == null ? DEFAULT_LEASE_LOCK_TIMEOUT : leaseLockTimeout;
    }

    /**
     * Creates an instance with every default, for tests and for applications wiring the view manager by hand.
     *
     * @return Configuration with the documented defaults.
     */
    public static ProjectionConfig defaults() {
        return new ProjectionConfig(null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Returns the number of attempts judged before the breaker may open.
     *
     * @return Sliding window size.
     */
    public int getBreakerWindowSize() {
        return breakerWindowSize;
    }

    /**
     * Returns the percentage of failed attempts that opens the breaker.
     *
     * @return Failure rate in percent.
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
     * Returns the growth factor applied to the wait after each further failed probe.
     *
     * @return Backoff multiplier.
     */
    public double getBreakerBackoffMultiplier() {
        return breakerBackoffMultiplier;
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
     * Returns the schedule for re-establishing a dropped wake-up subscription.
     *
     * @return Re-subscribe backoff.
     */
    public Backoff getResubscribeBackoff() {
        return resubscribeBackoff;
    }

    /**
     * Returns the bound for acquiring the projection lease lock.
     *
     * @return Lock timeout.
     */
    public Duration getLeaseLockTimeout() {
        return leaseLockTimeout;
    }

}
