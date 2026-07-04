package org.fuin.cqrs4j.jpa.pm;

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
 * A command produced by a process manager that is waiting to be dispatched to the command endpoint. Rows are
 * inserted within the same transaction as the process manager's state change (transactional outbox) and drained
 * asynchronously by the command queue executor.
 */
@NotThreadSafe
@Entity
@Table(name = "CQRS4J_PM_CMD_OUTBOX")
public class ProcessManagerCommandOutbox {

    @Id
    @Column(name = "CMD_ID", nullable = false, length = 250, updatable = false)
    @NotNull
    private String id;

    @Column(name = "CMD_TYPE", nullable = false, length = 250, updatable = false)
    @NotNull
    private String type;

    @Column(name = "CMD_CONTENT_TYPE", nullable = false, length = 200, updatable = false)
    @NotNull
    private String contentType;

    @Lob
    @Column(name = "CMD_JSON", nullable = false, updatable = false)
    @NotNull
    private String json;

    @Column(name = "CREATED_TS", nullable = false, updatable = false)
    @NotNull
    private Long createdTs;

    @Column(name = "RETRIES", nullable = false)
    @NotNull
    private Integer retries;

    @Column(name = "LAST_ERROR", length = 4000)
    @Nullable
    private String lastError;

    /**
     * JPA constructor.
     */
    protected ProcessManagerCommandOutbox() {
        super();
    }

    /**
     * Constructor with mandatory data. The number of retries starts at zero.
     *
     * @param id          Unique command identifier (used as primary key).
     * @param type        Type name used as path variable when calling the command endpoint.
     * @param contentType Full content type the command is serialized with (base type, encoding and version),
     *                    echoed as the HTTP {@code Content-Type} on delivery.
     * @param json        Serialized command.
     * @param createdTs   Creation timestamp (epoch milliseconds) used to drain the queue in order.
     */
    public ProcessManagerCommandOutbox(@NotNull final String id, @NotNull final String type, @NotNull final String contentType,
                                       @NotNull final String json, @NotNull final Long createdTs) {
        super();
        Contract.requireArgNotNull("id", id);
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("contentType", contentType);
        Contract.requireArgNotNull("json", json);
        Contract.requireArgNotNull("createdTs", createdTs);
        this.id = id;
        this.type = type;
        this.contentType = contentType;
        this.json = json;
        this.createdTs = createdTs;
        this.retries = 0;
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
     * Returns the command type name (path variable for the command endpoint).
     *
     * @return Command type.
     */
    @NotNull
    public String getType() {
        return type;
    }

    /**
     * Returns the full content type the command is serialized with (base type, encoding and version).
     *
     * @return Content type echoed as the HTTP {@code Content-Type} on delivery.
     */
    @NotNull
    public String getContentType() {
        return contentType;
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
     * Returns the creation timestamp (epoch milliseconds).
     *
     * @return Creation timestamp.
     */
    @NotNull
    public Long getCreatedTs() {
        return createdTs;
    }

    /**
     * Returns the number of failed delivery attempts so far.
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
     * @return Last error or {@literal null} if no attempt has failed yet.
     */
    @Nullable
    public String getLastError() {
        return lastError;
    }

    /**
     * Records a failed delivery attempt by incrementing the retry counter and storing the error.
     *
     * @param error Error message of the failed attempt.
     */
    public void recordFailure(@Nullable final String error) {
        this.retries = this.retries + 1;
        this.lastError = error;
    }

    @Override
    public String toString() {
        return ProcessManagerCommandOutbox.class.getSimpleName() + " [id=" + id + ", type=" + type + ", retries=" + retries + "]";
    }

}
