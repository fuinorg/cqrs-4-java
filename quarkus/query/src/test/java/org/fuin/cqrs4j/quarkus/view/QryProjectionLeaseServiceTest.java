package org.fuin.cqrs4j.quarkus.view;
import org.fuin.cqrs4j.jpa.query.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        testee.em = em;
    }

    @Test
    public void testAcquireFreshWhenNoLease() {
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", QryProjectionLeaseService.DEFAULT_LOCK_TIMEOUT_MILLIS))).thenReturn(null);
        assertThat(testee.acquire(STREAM, "A", TTL)).isTrue();
        verify(em).persist(any(QryProjectionLease.class));
    }

    @Test
    public void testAcquireBlockedByOtherLiveOwner() {
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now + TTL);
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", QryProjectionLeaseService.DEFAULT_LOCK_TIMEOUT_MILLIS))).thenReturn(held);
        assertThat(testee.acquire(STREAM, "A", TTL)).isFalse();
        assertThat(held.getOwner()).isEqualTo("B");
        verify(em, never()).persist(any());
    }

    @Test
    public void testAcquireTakesOverExpiredLease() {
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now - 1);
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", QryProjectionLeaseService.DEFAULT_LOCK_TIMEOUT_MILLIS))).thenReturn(held);
        assertThat(testee.acquire(STREAM, "A", TTL)).isTrue();
        assertThat(held.getOwner()).isEqualTo("A");
        assertThat(held.getExpiresAt()).isEqualTo(now + TTL);
    }

    @Test
    public void testAcquireRenewsOwnLease() {
        final QryProjectionLease held = new QryProjectionLease(STREAM, "A", now + 1);
        when(em.find(QryProjectionLease.class, "MyStream", LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", QryProjectionLeaseService.DEFAULT_LOCK_TIMEOUT_MILLIS))).thenReturn(held);
        assertThat(testee.acquire(STREAM, "A", TTL)).isTrue();
        assertThat(held.getExpiresAt()).isEqualTo(now + TTL);
    }

    @Test
    public void testRenewExtendsWhenOwned() {
        final QryProjectionLease held = new QryProjectionLease(STREAM, "A", now);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);
        testee.renew(STREAM, "A", TTL);
        assertThat(held.getExpiresAt()).isEqualTo(now + TTL);
    }

    @Test
    public void testRenewNoopWhenNotOwned() {
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now + 1);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);
        testee.renew(STREAM, "A", TTL);
        assertThat(held.getOwner()).isEqualTo("B");
        assertThat(held.getExpiresAt()).isEqualTo(now + 1);
    }

    @Test
    public void testReleaseRemovesWhenOwned() {
        final QryProjectionLease held = new QryProjectionLease(STREAM, "A", now + TTL);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);
        testee.release(STREAM, "A");
        verify(em).remove(held);
    }

    @Test
    public void testReleaseNoopWhenNotOwned() {
        final QryProjectionLease held = new QryProjectionLease(STREAM, "B", now + TTL);
        when(em.find(QryProjectionLease.class, "MyStream")).thenReturn(held);
        testee.release(STREAM, "A");
        verify(em, never()).remove(any());
    }

}
