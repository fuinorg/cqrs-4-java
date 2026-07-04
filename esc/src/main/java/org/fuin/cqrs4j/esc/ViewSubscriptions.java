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

import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EscApiUtils;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.esc.api.Subscription;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Manages "wake-up" subscriptions for the push-based projection mode. For each stream it opens a
 * {@link SubscribableEventStoreAsync} subscription to <em>new</em> events; when an event arrives the payload is
 * ignored and the supplied wake-up is run - the signal simply triggers the normal (checkpoint-based) catch-up
 * pass immediately instead of waiting for the next scheduled poll. Because the underlying store does not
 * re-subscribe automatically, a dropped subscription is re-established after a backoff (the poll remains as a
 * safety net in the meantime).
 * <p>
 * The re-subscribe scheduler is provided by the caller (and owned by it); {@link #close()} stops signalling and
 * unsubscribes but does not shut the scheduler down.
 */
@ThreadSafe
public final class ViewSubscriptions implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ViewSubscriptions.class);

    private final SubscribableEventStoreAsync store;

    private final ScheduledExecutorService resubscribeScheduler;

    private final long resubscribeBackoffMillis;

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    private volatile boolean closed;

    /**
     * Constructor with all mandatory data.
     *
     * @param store                    Subscribable event store.
     * @param resubscribeScheduler     Executor used to re-subscribe after a drop (owned by the caller).
     * @param resubscribeBackoffMillis Delay before a dropped subscription is re-established.
     */
    public ViewSubscriptions(final SubscribableEventStoreAsync store,
                             final ScheduledExecutorService resubscribeScheduler,
                             final long resubscribeBackoffMillis) {
        super();
        Contract.requireArgNotNull("store", store);
        Contract.requireArgNotNull("resubscribeScheduler", resubscribeScheduler);
        Contract.requireArgMin("resubscribeBackoffMillis", resubscribeBackoffMillis, 0);
        this.store = store;
        this.resubscribeScheduler = resubscribeScheduler;
        this.resubscribeBackoffMillis = resubscribeBackoffMillis;
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
                LOG.debug("Subscription for stream {} dropped, re-subscribing in {} ms: {}",
                        streamId.asString(), resubscribeBackoffMillis, ex == null ? "n/a" : ex.toString());
                scheduleResubscribe(streamId, onWakeup);
            }
        };
        store.subscribeToStream(streamId, EscApiUtils.SUBSCRIBE_TO_NEW_EVENTS, onEvent, onDrop)
                .thenAccept(subscription -> {
                    if (closed) {
                        // Raced with close(): tear down the just-created subscription.
                        store.unsubscribeFromStream(subscription);
                    } else {
                        subscriptions.put(streamId.asString(), subscription);
                    }
                })
                .exceptionally(throwable -> {
                    if (!closed) {
                        LOG.debug("Could not subscribe to stream {}, retrying in {} ms: {}",
                                streamId.asString(), resubscribeBackoffMillis, throwable.toString());
                        scheduleResubscribe(streamId, onWakeup);
                    }
                    return null;
                });
    }

    private void scheduleResubscribe(final StreamId streamId, final Runnable onWakeup) {
        try {
            resubscribeScheduler.schedule(() -> doSubscribe(streamId, onWakeup), resubscribeBackoffMillis,
                    TimeUnit.MILLISECONDS);
        } catch (final RejectedExecutionException ex) {
            // Scheduler already shut down (closing) - nothing to do.
            LOG.trace("Re-subscribe rejected (shutting down) for stream {}", streamId.asString());
        }
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
