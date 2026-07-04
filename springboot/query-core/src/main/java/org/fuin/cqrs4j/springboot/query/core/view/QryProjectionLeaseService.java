package org.fuin.cqrs4j.springboot.query.core.view;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.esc.ProjectionLeaseService;
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relational {@link ProjectionLeaseService} backed by the {@code SPRING_QRY_PROJECTION_LEASE} table
 * ({@link QryProjectionLease}). A pessimistic write lock on acquire serializes competing instances, and the
 * stored expiry lets a crashed owner's lease be taken over after its time-to-live elapses.
 */
@ThreadSafe
@Repository
public class QryProjectionLeaseService implements ProjectionLeaseService {

    private static final String ARG_STREAM_ID = "streamId";

    private static final String ARG_OWNER = "owner";

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public boolean acquire(@NotNull final StreamId streamId, @NotNull final String owner, final long ttlMillis) {
        Contract.requireArgNotNull(ARG_STREAM_ID, streamId);
        Contract.requireArgNotNull(ARG_OWNER, owner);
        final long now = now();
        final QryProjectionLease lease = em.find(QryProjectionLease.class, streamId.asString(),
                LockModeType.PESSIMISTIC_WRITE);
        if (lease == null) {
            em.persist(new QryProjectionLease(streamId, owner, now + ttlMillis));
            return true;
        }
        if (owner.equals(lease.getOwner()) || now >= lease.getExpiresAt()) {
            lease.grantTo(owner, now + ttlMillis);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void renew(@NotNull final StreamId streamId, @NotNull final String owner, final long ttlMillis) {
        Contract.requireArgNotNull(ARG_STREAM_ID, streamId);
        Contract.requireArgNotNull(ARG_OWNER, owner);
        final QryProjectionLease lease = em.find(QryProjectionLease.class, streamId.asString());
        if (lease != null && owner.equals(lease.getOwner())) {
            lease.grantTo(owner, now() + ttlMillis);
        }
    }

    @Override
    @Transactional
    public void release(@NotNull final StreamId streamId, @NotNull final String owner) {
        Contract.requireArgNotNull(ARG_STREAM_ID, streamId);
        Contract.requireArgNotNull(ARG_OWNER, owner);
        final QryProjectionLease lease = em.find(QryProjectionLease.class, streamId.asString());
        if (lease != null && owner.equals(lease.getOwner())) {
            em.remove(lease);
        }
    }

    /**
     * Returns the current time in epoch milliseconds. Overridable for testing.
     *
     * @return Current time.
     */
    protected long now() {
        return System.currentTimeMillis();
    }

}
