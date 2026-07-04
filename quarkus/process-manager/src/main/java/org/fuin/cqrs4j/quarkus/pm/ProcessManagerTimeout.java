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
 * A pending process timeout: a process manager step that awaits a reply, with a deadline by which the reply is
 * expected. Rows are inserted/updated within the process manager view's transaction (via
 * {@link org.fuin.cqrs4j.core.ProcessTimeoutService}) and swept by the {@link QuarkusProcessTimeoutSweeper}; a row that
 * passes its deadline without being cancelled is handed to the application's timeout handler. The primary key is
 * the process instance id, so there is one pending timeout per process.
 */
@NotThreadSafe
@Entity
@Table(name = "QUARKUS_PM_TIMEOUT")
public class ProcessManagerTimeout {

    @Id
    @Column(name = "PROCESS_ID", nullable = false, length = 250, updatable = false)
    @NotNull
    private String processId;

    @Column(name = "PROCESS_TYPE", nullable = false, length = 250)
    @NotNull
    private String processType;

    @Column(name = "PROCESS_VERSION", nullable = false)
    @NotNull
    private Integer processVersion;

    @Column(name = "DEADLINE_TS", nullable = false)
    @NotNull
    private Long deadlineTs;

    @Lob
    @Column(name = "PAYLOAD")
    @Nullable
    private String payload;

    @Column(name = "CREATED_TS", nullable = false)
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
    protected ProcessManagerTimeout() {
        super();
    }

    /**
     * Constructor with mandatory data. The number of retries starts at zero.
     *
     * @param processId      Unique process instance id (primary key).
     * @param processType    Process type name.
     * @param processVersion Process (definition) version.
     * @param deadlineTs     Epoch millisecond time at which the timeout becomes due.
     * @param payload        Optional application data carried to the handler.
     * @param createdTs      Creation timestamp (epoch milliseconds).
     */
    public ProcessManagerTimeout(@NotNull final String processId, @NotNull final String processType,
                                 @NotNull final Integer processVersion, @NotNull final Long deadlineTs,
                                 @Nullable final String payload, @NotNull final Long createdTs) {
        super();
        Contract.requireArgNotNull("processId", processId);
        Contract.requireArgNotNull("processType", processType);
        Contract.requireArgNotNull("processVersion", processVersion);
        Contract.requireArgNotNull("deadlineTs", deadlineTs);
        Contract.requireArgNotNull("createdTs", createdTs);
        this.processId = processId;
        this.processType = processType;
        this.processVersion = processVersion;
        this.deadlineTs = deadlineTs;
        this.payload = payload;
        this.createdTs = createdTs;
        this.retries = 0;
    }

    /**
     * Returns the unique process instance id.
     *
     * @return Process id.
     */
    @NotNull
    public String getProcessId() {
        return processId;
    }

    /**
     * Returns the process type name.
     *
     * @return Process type.
     */
    @NotNull
    public String getProcessType() {
        return processType;
    }

    /**
     * Returns the process (definition) version.
     *
     * @return Process version.
     */
    @NotNull
    public Integer getProcessVersion() {
        return processVersion;
    }

    /**
     * Returns the epoch millisecond deadline.
     *
     * @return Deadline timestamp.
     */
    @NotNull
    public Long getDeadlineTs() {
        return deadlineTs;
    }

    /**
     * Returns the optional application payload.
     *
     * @return Payload or {@literal null}.
     */
    @Nullable
    public String getPayload() {
        return payload;
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
     * Returns the number of times handling this timeout has failed so far.
     *
     * @return Retry count.
     */
    @NotNull
    public Integer getRetries() {
        return retries;
    }

    /**
     * Returns the error message of the last failed handling attempt.
     *
     * @return Last error or {@literal null} if no attempt has failed yet.
     */
    @Nullable
    public String getLastError() {
        return lastError;
    }

    /**
     * Re-arms the pending timeout with a new deadline (and type/version/payload), resetting the retry counter.
     *
     * @param processType    Process type name.
     * @param processVersion Process (definition) version.
     * @param deadlineTs     New epoch millisecond deadline.
     * @param payload        Optional application data.
     */
    public void rearm(@NotNull final String processType, @NotNull final Integer processVersion,
                      @NotNull final Long deadlineTs, @Nullable final String payload) {
        Contract.requireArgNotNull("processType", processType);
        Contract.requireArgNotNull("processVersion", processVersion);
        Contract.requireArgNotNull("deadlineTs", deadlineTs);
        this.processType = processType;
        this.processVersion = processVersion;
        this.deadlineTs = deadlineTs;
        this.payload = payload;
        this.retries = 0;
        this.lastError = null;
    }

    /**
     * Records a failed handling attempt by incrementing the retry counter and storing the error.
     *
     * @param error Error message of the failed attempt.
     */
    public void recordFailure(@Nullable final String error) {
        this.retries = this.retries + 1;
        this.lastError = error;
    }

    @Override
    public String toString() {
        return ProcessManagerTimeout.class.getSimpleName() + " [processId=" + processId + ", processType=" + processType
                + ", processVersion=" + processVersion + ", deadlineTs=" + deadlineTs + ", retries=" + retries + "]";
    }

}
