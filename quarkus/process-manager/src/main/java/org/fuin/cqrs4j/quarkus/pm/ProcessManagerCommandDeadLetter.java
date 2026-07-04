package org.fuin.cqrs4j.quarkus.pm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.NotThreadSafe;
import org.jspecify.annotations.Nullable;

/**
 * A command that could not be delivered to the command endpoint after exhausting all retries. It is moved here by
 * the {@link QuarkusCommandQueueExecutor} so that it no longer blocks the outbox queue while remaining available
 * for manual inspection and re-processing.
 */
@NotThreadSafe
@Entity
@Table(name = "QUARKUS_PM_CMD_DEAD_LETTER")
public class ProcessManagerCommandDeadLetter {

    @Id
    @Column(name = "CMD_ID", nullable = false, length = 250, updatable = false)
    @NotNull
    private String id;

    @Column(name = "CMD_TYPE", nullable = false, length = 250, updatable = false)
    @NotNull
    private String type;

    @Lob
    @Column(name = "CMD_JSON", nullable = false, updatable = false)
    @NotNull
    private String json;

    @Column(name = "CREATED_TS", nullable = false, updatable = false)
    @NotNull
    private Long createdTs;

    @Column(name = "FAILED_TS", nullable = false, updatable = false)
    @NotNull
    private Long failedTs;

    @Column(name = "RETRIES", nullable = false, updatable = false)
    @NotNull
    private Integer retries;

    @Lob
    @Column(name = "ERROR", updatable = false)
    @Nullable
    private String error;

    /**
     * JPA constructor.
     */
    protected ProcessManagerCommandDeadLetter() {
        super();
    }

    /**
     * Constructor with mandatory data.
     *
     * @param id        Unique command identifier (used as primary key).
     * @param type      Type name used as path variable when calling the command endpoint.
     * @param json      Serialized command (JSON).
     * @param createdTs Original creation timestamp of the outbox entry (epoch milliseconds).
     * @param failedTs  Timestamp the command was moved to the dead-letter table (epoch milliseconds).
     * @param retries   Number of delivery attempts that were made.
     * @param error     Error message of the last failed delivery attempt.
     */
    public ProcessManagerCommandDeadLetter(@NotNull final String id, @NotNull final String type, @NotNull final String json,
                                           @NotNull final Long createdTs, @NotNull final Long failedTs,
                                           @NotNull final Integer retries, @Nullable final String error) {
        super();
        Contract.requireArgNotNull("id", id);
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("json", json);
        Contract.requireArgNotNull("createdTs", createdTs);
        Contract.requireArgNotNull("failedTs", failedTs);
        Contract.requireArgNotNull("retries", retries);
        this.id = id;
        this.type = type;
        this.json = json;
        this.createdTs = createdTs;
        this.failedTs = failedTs;
        this.retries = retries;
        this.error = error;
    }

    /**
     * Creates a dead-letter entry from a (durably failed) outbox entry.
     *
     * @param outbox   Outbox entry that exhausted its retries.
     * @param failedTs Timestamp the command was moved to the dead-letter table (epoch milliseconds).
     * @return New dead-letter entry.
     */
    public static ProcessManagerCommandDeadLetter fromOutbox(@NotNull final ProcessManagerCommandOutbox outbox, @NotNull final Long failedTs) {
        Contract.requireArgNotNull("outbox", outbox);
        Contract.requireArgNotNull("failedTs", failedTs);
        return new ProcessManagerCommandDeadLetter(outbox.getId(), outbox.getType(), outbox.getJson(), outbox.getCreatedTs(),
                failedTs, outbox.getRetries(), outbox.getLastError());
    }

    /**
     * Returns the unique command identifier.
     *
     * @return Command ID.
     */
    @NotNull
    public String getId() {
        return id;
    }

    /**
     * Returns the command type name.
     *
     * @return Command type.
     */
    @NotNull
    public String getType() {
        return type;
    }

    /**
     * Returns the serialized command.
     *
     * @return Command JSON.
     */
    @NotNull
    public String getJson() {
        return json;
    }

    /**
     * Returns the original creation timestamp of the outbox entry (epoch milliseconds).
     *
     * @return Creation timestamp.
     */
    @NotNull
    public Long getCreatedTs() {
        return createdTs;
    }

    /**
     * Returns the timestamp the command was moved to the dead-letter table (epoch milliseconds).
     *
     * @return Failure timestamp.
     */
    @NotNull
    public Long getFailedTs() {
        return failedTs;
    }

    /**
     * Returns the number of delivery attempts that were made.
     *
     * @return Retry count.
     */
    @NotNull
    public Integer getRetries() {
        return retries;
    }

    /**
     * Returns the error message of the last failed delivery attempt.
     *
     * @return Last error or {@literal null}.
     */
    @Nullable
    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return ProcessManagerCommandDeadLetter.class.getSimpleName() + " [id=" + id + ", type=" + type + ", retries=" + retries + "]";
    }

}
