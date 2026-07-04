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
import org.fuin.esc.api.EventId;
import org.fuin.esc.api.SimpleCommonEvent;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.esc.api.Subscription;
import org.fuin.esc.api.TypeName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link ViewSubscriptions} class using a fake subscribable store whose captured callbacks are
 * invoked directly.
 */
public class ViewSubscriptionsTest {

    private static final StreamId STREAM = new SimpleStreamId("projection-MyView");

    @Test
    public void testWakeupOnEvent() {

        // PREPARE
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final FakeSubscribableStore store = new FakeSubscribableStore(null);
            final ViewSubscriptions testee = new ViewSubscriptions(store, scheduler, 10);
            final AtomicInteger wakeups = new AtomicInteger();

            // TEST
            testee.subscribe(STREAM, wakeups::incrementAndGet);
            store.lastOnEvent().accept(store.lastSubscription(), event("1"));
            store.lastOnEvent().accept(store.lastSubscription(), event("2"));

            // VERIFY: each arriving event fires the wake-up once
            assertThat(wakeups.get()).isEqualTo(2);
            testee.close();
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void testResubscribeOnDrop() throws InterruptedException {

        // PREPARE
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final CountDownLatch subscribed = new CountDownLatch(2); // initial + one re-subscribe
            final FakeSubscribableStore store = new FakeSubscribableStore(subscribed);
            final ViewSubscriptions testee = new ViewSubscriptions(store, scheduler, 20);
            testee.subscribe(STREAM, () -> {
            });

            // TEST: drop the subscription
            store.lastOnDrop().accept(store.lastSubscription(), new RuntimeException("dropped"));

            // VERIFY: it re-subscribes after the backoff
            assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(store.subscribeCount()).isEqualTo(2);
            testee.close();
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void testCloseUnsubscribes() {

        // PREPARE
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final FakeSubscribableStore store = new FakeSubscribableStore(null);
            final ViewSubscriptions testee = new ViewSubscriptions(store, scheduler, 10);
            testee.subscribe(STREAM, () -> {
            });

            // TEST
            testee.close();

            // VERIFY
            assertThat(store.subscribeCount()).isEqualTo(1);
            assertThat(store.unsubscribeCount()).isEqualTo(1);
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static CommonEvent event(final String data) {
        return new SimpleCommonEvent(new EventId(), new TypeName("MyEvent"), data, null);
    }

    /**
     * Fake {@link SubscribableEventStoreAsync} that captures the callbacks and counts subscribe/unsubscribe.
     */
    private static final class FakeSubscribableStore implements SubscribableEventStoreAsync {

        private final CountDownLatch subscribeLatch;

        private final List<BiConsumer<Subscription, CommonEvent>> onEvents = new CopyOnWriteArrayList<>();

        private final List<BiConsumer<Subscription, Exception>> onDrops = new CopyOnWriteArrayList<>();

        private final AtomicInteger subscribes = new AtomicInteger();

        private final AtomicInteger unsubscribes = new AtomicInteger();

        private volatile Subscription lastSubscription;

        FakeSubscribableStore(final CountDownLatch subscribeLatch) {
            this.subscribeLatch = subscribeLatch;
        }

        @Override
        public CompletableFuture<Subscription> subscribeToStream(final StreamId streamId, final long eventNumber,
                final BiConsumer<Subscription, CommonEvent> onEvent,
                final BiConsumer<Subscription, Exception> onDrop) {
            onEvents.add(onEvent);
            onDrops.add(onDrop);
            lastSubscription = new Subscription(streamId, null) {
            };
            subscribes.incrementAndGet();
            if (subscribeLatch != null) {
                subscribeLatch.countDown();
            }
            return CompletableFuture.completedFuture(lastSubscription);
        }

        @Override
        public CompletableFuture<Void> unsubscribeFromStream(final Subscription subscription) {
            unsubscribes.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> open() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            // Nothing to do
        }

        BiConsumer<Subscription, CommonEvent> lastOnEvent() {
            return onEvents.get(onEvents.size() - 1);
        }

        BiConsumer<Subscription, Exception> lastOnDrop() {
            return onDrops.get(onDrops.size() - 1);
        }

        Subscription lastSubscription() {
            return lastSubscription;
        }

        int subscribeCount() {
            return subscribes.get();
        }

        int unsubscribeCount() {
            return unsubscribes.get();
        }

    }

}
