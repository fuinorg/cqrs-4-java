/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.esc;

import org.fuin.esc.api.Backoff;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EscApiUtils;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.esc.api.Subscription;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Manages "wake-up" subscriptions for the push-based projection mode. For each stream it opens a
 * {@link SubscribableEventStoreAsync} subscription to <em>new</em> events; when an event arrives the payload is
 * ignored and the supplied wake-up is run - the signal simply triggers the normal (checkpoint-based) catch-up
 * pass immediately instead of waiting for the next scheduled poll. Because the underlying store does not
 * re-subscribe automatically, a dropped subscription is re-established after a backoff (the poll remains as a
 * safety net in the meantime).
 * <p>
 * The re-subscribe schedule is an event-store-commons {@link Backoff}: exponential, capped and jittered. A
 * fixed delay would have every instance of a scaled-out service reconnect in lockstep and hit the store as one
 * burst the moment it returns, and would keep hammering it at the same rate throughout a long outage. The
 * attempt counter is per stream and is reset whenever a subscription is established, so a later outage gets
 * the full schedule again rather than continuing at the delay the previous one ended with.
 * <p>
 * The same schedule covers the <em>first</em> subscribe, which matters when the application starts before the
 * store is reachable. This is why the subscription handling stays here rather than delegating to the
 * event-store-commons {@code ReconnectingSubscribableEventStore}: that decorator deliberately does not retry
 * the initial subscribe, and its main feature - resuming after the last delivered event - does not apply to a
 * wake-up subscription, which follows new events only and has no position to resume from.
 * <p>
 * Losing a subscription only costs latency, never correctness: until it is back the catch-up pass runs on its
 * normal schedule and reads from its checkpoint. That is why exhausting the attempt budget is logged and
 * accepted rather than escalated.
 * <p>
 * The re-subscribe scheduler is provided by the caller (and owned by it); {@link #close()} stops signalling and
 * unsubscribes but does not shut the scheduler down.
 */
@ThreadSafe
public final class ViewSubscriptions implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ViewSubscriptions.class);

    private final SubscribableEventStoreAsync store;

    private final ScheduledExecutorService resubscribeScheduler;

    private final Backoff backoff;

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    private volatile boolean closed;

    /**
     * Constructor with a fixed re-subscribe delay.
     *
     * @param store                    Subscribable event store.
     * @param resubscribeScheduler     Executor used to re-subscribe after a drop (owned by the caller).
     * @param resubscribeBackoffMillis Delay before a dropped subscription is re-established.
     *
     * @deprecated Use {@link #ViewSubscriptions(SubscribableEventStoreAsync, ScheduledExecutorService, Backoff)}
     *             instead. A fixed delay makes every instance reconnect in lockstep and never backs off during
     *             a long outage; this constructor now builds an equivalent unjittered, non-growing
     *             {@link Backoff} so existing callers keep their exact behaviour.
     */
    @Deprecated(since = "0.9.0", forRemoval = false)
    public ViewSubscriptions(final SubscribableEventStoreAsync store,
                             final ScheduledExecutorService resubscribeScheduler,
                             final long resubscribeBackoffMillis) {
        this(store, resubscribeScheduler, fixedDelay(resubscribeBackoffMillis));
        Contract.requireArgMin("resubscribeBackoffMillis", resubscribeBackoffMillis, 0);
    }

    /**
     * Constructor with all mandatory data.
     *
     * @param store                Subscribable event store.
     * @param resubscribeScheduler Executor used to re-subscribe after a drop (owned by the caller).
     * @param backoff              Delay schedule between (re-)subscribe attempts. {@link Backoff#DEFAULT} is a
     *                             reasonable choice: 500 ms doubling up to 30 s with 50% jitter and no attempt
     *                             limit, which keeps trying for as long as the application runs.
     */
    public ViewSubscriptions(final SubscribableEventStoreAsync store,
                             final ScheduledExecutorService resubscribeScheduler,
                             final Backoff backoff) {
        super();
        Contract.requireArgNotNull("store", store);
        Contract.requireArgNotNull("resubscribeScheduler", resubscribeScheduler);
        Contract.requireArgNotNull("backoff", backoff);
        this.store = store;
        this.resubscribeScheduler = resubscribeScheduler;
        this.backoff = backoff;
    }

    /**
     * Builds the schedule that reproduces the old fixed-delay behaviour.
     *
     * @param millis Delay to use for every attempt.
     * @return Backoff that neither grows nor jitters.
     */
    private static Backoff fixedDelay(final long millis) {
        // Backoff requires a positive initial delay; a caller that passed 0 wanted "as soon as possible".
        final Duration delay = Duration.ofMillis(Math.max(1, millis));
        return new Backoff(delay, delay, 1.0, 0.0, Backoff.UNLIMITED_ATTEMPTS);
    }

    /**
     * Opens a wake-up subscription for the given stream. The {@code onWakeup} runnable is executed (on the
     * store's callback thread) whenever a new event arrives; it must hand off quickly and not block.
     *
     * @param streamId Stream to subscribe to.
     * @param onWakeup Action to run when a new event signals that a catch-up pass should run.
     */
    public void subscribe(final StreamId streamId, final Runnable onWakeup) {
        Contract.requireArgNotNull("streamId", streamId);
        Contract.requireArgNotNull("onWakeup", onWakeup);
        doSubscribe(streamId, onWakeup);
    }

    private void doSubscribe(final StreamId streamId, final Runnable onWakeup) {
        if (closed) {
            return;
        }
        final BiConsumer<Subscription, CommonEvent> onEvent = (sub, event) -> {
            // The payload is ignored - an arrival is only a low-latency signal to run the catch-up pass.
            try {
                onWakeup.run();
            } catch (final RuntimeException ex) {
                LOG.error("Error running wake-up for stream {}", streamId.asString(), ex);
            }
        };
        final BiConsumer<Subscription, Exception> onDrop = (sub, ex) -> {
            subscriptions.remove(streamId.asString());
            if (!closed) {
                scheduleResubscribe(streamId, onWakeup, "dropped: " + (ex == null ? "n/a" : ex.toString()));
            }
        };
        store.subscribeToStream(streamId, EscApiUtils.SUBSCRIBE_TO_NEW_EVENTS, onEvent, onDrop)
                .thenAccept(subscription -> {
                    if (closed) {
                        // Raced with close(): tear down the just-created subscription.
                        store.unsubscribeFromStream(subscription);
                    } else {
                        subscriptions.put(streamId.asString(), subscription);
                        // A later outage starts from the beginning of the schedule, not from the delay this
                        // one ended with.
                        attempts(streamId).set(0);
                    }
                })
                .exceptionally(throwable -> {
                    if (!closed) {
                        scheduleResubscribe(streamId, onWakeup, "could not subscribe: " + throwable);
                    }
                    return null;
                });
    }

    /**
     * Schedules the next (re-)subscribe attempt, or gives up and leaves the view on its poll.
     *
     * @param streamId Stream to re-subscribe to.
     * @param onWakeup Action to run when a new event arrives.
     * @param reason   Why another attempt is needed, for the log.
     */
    private void scheduleResubscribe(final StreamId streamId, final Runnable onWakeup, final String reason) {
        final int attempt = attempts(streamId).incrementAndGet();
        if (!backoff.allowsAttempt(attempt)) {
            // Only latency is lost - the catch-up pass keeps running on its schedule and reads from its
            // checkpoint - so this is reported and accepted rather than escalated.
            LOG.error("Wake-up subscription for stream {} could not be established within {} attempts, "
                            + "staying on the poll ({})",
                    streamId.asString(), backoff.maxAttempts(), reason);
            return;
        }
        final long delayMillis = backoff.delay(attempt).toMillis();
        LOG.debug("Wake-up subscription for stream {}, attempt {} in {} ms ({})",
                streamId.asString(), attempt, delayMillis, reason);
        try {
            resubscribeScheduler.schedule(() -> doSubscribe(streamId, onWakeup), delayMillis,
                    TimeUnit.MILLISECONDS);
        } catch (final RejectedExecutionException ex) {
            // Scheduler already shut down (closing) - nothing to do.
            LOG.trace("Re-subscribe rejected (shutting down) for stream {}", streamId.asString());
        }
    }

    private AtomicInteger attempts(final StreamId streamId) {
        return attempts.computeIfAbsent(streamId.asString(), key -> new AtomicInteger());
    }

    @Override
    public void close() {
        closed = true;
        for (final Subscription subscription : subscriptions.values()) {
            try {
                store.unsubscribeFromStream(subscription);
            } catch (final RuntimeException ex) {
                LOG.debug("Error unsubscribing from stream {}: {}", subscription.getStreamId().asString(),
                        ex.toString());
            }
        }
        subscriptions.clear();
    }

}
