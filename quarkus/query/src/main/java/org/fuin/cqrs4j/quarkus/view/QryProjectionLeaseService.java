package org.fuin.cqrs4j.quarkus.view;
import org.fuin.cqrs4j.jpa.query.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.esc.ProjectionLeaseService;
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Relational {@link ProjectionLeaseService} backed by the {@code QUARKUS_QRY_PROJECTION_LEASE} table
 * ({@link QryProjectionLease}). A pessimistic write lock on acquire serializes competing instances, and the
 * stored expiry lets a crashed owner's lease be taken over after its time-to-live elapses.
 */
@ThreadSafe
@ApplicationScoped
public class QryProjectionLeaseService implements ProjectionLeaseService {

    private static final String ARG_STREAM_ID = "streamId";

    private static final String ARG_OWNER = "owner";

    @Inject
    EntityManager em;

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
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
    public void renew(@NotNull final StreamId streamId, @NotNull final String owner, final long ttlMillis) {
        Contract.requireArgNotNull(ARG_STREAM_ID, streamId);
        Contract.requireArgNotNull(ARG_OWNER, owner);
        final QryProjectionLease lease = em.find(QryProjectionLease.class, streamId.asString());
        if (lease != null && owner.equals(lease.getOwner())) {
            lease.grantTo(owner, now() + ttlMillis);
        }
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
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
