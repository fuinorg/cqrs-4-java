package org.fuin.cqrs4j.jpa.query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.NotThreadSafe;

/**
 * Distributed lease that grants a single application instance the right to process a projection. The primary
 * key is the projection stream's {@link StreamId#asString()}; {@code owner} identifies the holding instance and
 * {@code expiresAt} is the epoch-millisecond time at which the lease expires.
 */
@NotThreadSafe
@Entity
@Table(name = "CQRS4J_QRY_PROJECTION_LEASE")
public class QryProjectionLease {

    @Id
    @Column(name = "STREAM_ID", nullable = false, length = 250, updatable = false)
    @NotNull
    private String streamId;

    @Column(name = "OWNER", nullable = false, length = 100)
    @NotNull
    private String owner;

    @Column(name = "EXPIRES_AT", nullable = false)
    private long expiresAt;

    /**
     * JPA constructor.
     */
    protected QryProjectionLease() {
        super();
    }

    /**
     * Constructor with mandatory data.
     *
     * @param streamId  Unique projection stream identifier.
     * @param owner     Identifier of the holding application instance.
     * @param expiresAt Epoch millisecond time at which the lease expires.
     */
    public QryProjectionLease(@NotNull final StreamId streamId, @NotNull final String owner, final long expiresAt) {
        super();
        Contract.requireArgNotNull("streamId", streamId);
        Contract.requireArgNotNull("owner", owner);
        this.streamId = streamId.asString();
        this.owner = owner;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the unique projection stream identifier.
     *
     * @return Stream ID (the {@code asString()} form).
     */
    @NotNull
    public String getStreamId() {
        return streamId;
    }

    /**
     * Returns the identifier of the holding application instance.
     *
     * @return Owner.
     */
    @NotNull
    public String getOwner() {
        return owner;
    }

    /**
     * Returns the epoch millisecond time at which the lease expires.
     *
     * @return Expiry time.
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the holding application instance and expiry time (used to take over an expired or own lease).
     *
     * @param owner     New owner.
     * @param expiresAt New expiry time.
     */
    public void grantTo(@NotNull final String owner, final long expiresAt) {
        Contract.requireArgNotNull("owner", owner);
        this.owner = owner;
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "QryProjectionLease [streamId=" + streamId + ", owner=" + owner + ", expiresAt=" + expiresAt + "]";
    }

}
