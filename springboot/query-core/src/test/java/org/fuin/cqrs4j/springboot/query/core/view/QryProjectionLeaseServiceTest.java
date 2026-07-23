package org.fuin.cqrs4j.springboot.query.core.view;
import org.fuin.cqrs4j.jpa.query.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link QryProjectionLeaseService} class with a mocked {@link EntityManager} and a controllable
 * clock.
 */
public class QryProjectionLeaseServiceTest {

    private static final StreamId STREAM = new SimpleStreamId("MyStream");

    private static final long TTL = 5_000L;

    private EntityManager em;

    private long now = 1_000L;

    private QryProjectionLeaseService testee;

    @BeforeEach
    public void setup() {
        em = mock(EntityManager.class);
        testee = new QryProjectionLeaseService() {
            @Override
            protected long now() {
                return now;
            }
        };
        ReflectionTestUtils.setField(testee, "em", em);
    }

    @Test
    public void testAcquireFreshWhenNoLease() {

        // PREPARE
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", (int) ProjectionConfig.DEFAULT_LEASE_LOCK_TIMEOUT.toMillis()))).thenReturn(null);

        // TEST & VERIFY
        assertThat(testee.acquire(STREAM, "A", TTL)).isTrue();
        verify(em).persist(any(QryProjectionLease.class));

    }

    @Test
    public void testAcquireBlockedByOtherLiveOwner() {

        // PREPARE: owned by B, expires in the future
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now + TTL);
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", (int) ProjectionConfig.DEFAULT_LEASE_LOCK_TIMEOUT.toMillis()))).thenReturn(held);

        // TEST & VERIFY
        assertThat(testee.acquire(STREAM, "A", TTL)).isFalse();
        assertThat(held.getOwner()).isEqualTo("B");
        verify(em, never()).persist(any());

    }

    @Test
    public void testAcquireTakesOverExpiredLease() {

        // PREPARE: owned by B but already expired
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now - 1);
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", (int) ProjectionConfig.DEFAULT_LEASE_LOCK_TIMEOUT.toMillis()))).thenReturn(held);

        // TEST & VERIFY
        assertThat(testee.acquire(STREAM, "A", TTL)).isTrue();
        assertThat(held.getOwner()).isEqualTo("A");
        assertThat(held.getExpiresAt()).isEqualTo(now + TTL);

    }

    @Test
    public void testAcquireRenewsOwnLease() {

        // PREPARE: already owned by A
        final QryProjectionLease held = new QryProjectionLease(STREAM, "A", now + 1);
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", (int) ProjectionConfig.DEFAULT_LEASE_LOCK_TIMEOUT.toMillis()))).thenReturn(held);

        // TEST & VERIFY
        assertThat(testee.acquire(STREAM, "A", TTL)).isTrue();
        assertThat(held.getExpiresAt()).isEqualTo(now + TTL);

    }

    @Test
    public void testRenewExtendsWhenOwned() {

        // PREPARE
        final QryProjectionLease held = new QryProjectionLease(STREAM, "A", now);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);

        // TEST
        testee.renew(STREAM, "A", TTL);

        // VERIFY
        assertThat(held.getExpiresAt()).isEqualTo(now + TTL);

    }

    @Test
    public void testRenewNoopWhenNotOwned() {

        // PREPARE: owned by B
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now + 1);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);

        // TEST
        testee.renew(STREAM, "A", TTL);

        // VERIFY: unchanged
        assertThat(held.getOwner()).isEqualTo("B");
        assertThat(held.getExpiresAt()).isEqualTo(now + 1);

    }

    @Test
    public void testReleaseRemovesWhenOwned() {

        // PREPARE
        final QryProjectionLease held = new QryProjectionLease(STREAM, "A", now + TTL);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);

        // TEST
        testee.release(STREAM, "A");

        // VERIFY
        verify(em).remove(held);

    }

    @Test
    public void testReleaseNoopWhenNotOwned() {

        // PREPARE: owned by B
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now + TTL);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);

        // TEST
        testee.release(STREAM, "A");

        // VERIFY
        verify(em, never()).remove(any());

    }

}
