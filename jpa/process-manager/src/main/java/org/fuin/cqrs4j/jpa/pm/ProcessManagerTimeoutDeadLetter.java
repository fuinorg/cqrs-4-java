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
 * A process timeout whose handler kept failing until it exhausted all retries. It is moved here by the
 * process-timeout sweeper so that it no longer loops forever while remaining visible for manual inspection and
 * re-processing.
 */
@NotThreadSafe
@Entity
@Table(name = "CQRS4J_PM_TIMEOUT_DEAD_LETTER")
public class ProcessManagerTimeoutDeadLetter {

    @Id
    @Column(name = "PROCESS_ID", nullable = false, length = 250, updatable = false)
    @NotNull
    private String processId;

    @Column(name = "PROCESS_TYPE", nullable = false, length = 250, updatable = false)
    @NotNull
    private String processType;

    @Column(name = "PROCESS_VERSION", nullable = false, updatable = false)
    @NotNull
    private Integer processVersion;

    @Column(name = "DEADLINE_TS", nullable = false, updatable = false)
    @NotNull
    private Long deadlineTs;

    @Lob
    @Column(name = "PAYLOAD", updatable = false)
    @Nullable
    private String payload;

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
    protected ProcessManagerTimeoutDeadLetter() {
        super();
    }

    /**
     * Constructor with mandatory data.
     *
     * @param processId      Unique process instance id (primary key).
     * @param processType    Process type name.
     * @param processVersion Process (definition) version.
     * @param deadlineTs     Epoch millisecond deadline that elapsed.
     * @param payload        Optional application data.
     * @param createdTs      Original creation timestamp of the timeout (epoch milliseconds).
     * @param failedTs       Timestamp the timeout was moved to the dead-letter table (epoch milliseconds).
     * @param retries        Number of handling attempts that were made.
     * @param error          Error message of the last failed handling attempt.
     */
    public ProcessManagerTimeoutDeadLetter(@NotNull final String processId, @NotNull final String processType,
                                           @NotNull final Integer processVersion, @NotNull final Long deadlineTs,
                                           @Nullable final String payload, @NotNull final Long createdTs,
                                           @NotNull final Long failedTs, @NotNull final Integer retries,
                                           @Nullable final String error) {
        super();
        Contract.requireArgNotNull("processId", processId);
        Contract.requireArgNotNull("processType", processType);
        Contract.requireArgNotNull("processVersion", processVersion);
        Contract.requireArgNotNull("deadlineTs", deadlineTs);
        Contract.requireArgNotNull("createdTs", createdTs);
        Contract.requireArgNotNull("failedTs", failedTs);
        Contract.requireArgNotNull("retries", retries);
        this.processId = processId;
        this.processType = processType;
        this.processVersion = processVersion;
        this.deadlineTs = deadlineTs;
        this.payload = payload;
        this.createdTs = createdTs;
        this.failedTs = failedTs;
        this.retries = retries;
        this.error = error;
    }

    /**
     * Creates a dead-letter entry from a (durably failed) pending timeout.
     *
     * @param timeout  Timeout that exhausted its retries.
     * @param failedTs Timestamp the timeout was moved to the dead-letter table (epoch milliseconds).
     * @return New dead-letter entry.
     */
    public static ProcessManagerTimeoutDeadLetter fromTimeout(@NotNull final ProcessManagerTimeout timeout,
                                                              @NotNull final Long failedTs) {
        Contract.requireArgNotNull("timeout", timeout);
        Contract.requireArgNotNull("failedTs", failedTs);
        return new ProcessManagerTimeoutDeadLetter(timeout.getProcessId(), timeout.getProcessType(),
                timeout.getProcessVersion(), timeout.getDeadlineTs(), timeout.getPayload(), timeout.getCreatedTs(),
                failedTs, timeout.getRetries(), timeout.getLastError());
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
     * Returns the epoch millisecond deadline that elapsed.
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
     * Returns the original creation timestamp of the timeout (epoch milliseconds).
     *
     * @return Creation timestamp.
     */
    @NotNull
    public Long getCreatedTs() {
        return createdTs;
    }

    /**
     * Returns the timestamp the timeout was moved to the dead-letter table (epoch milliseconds).
     *
     * @return Failure timestamp.
     */
    @NotNull
    public Long getFailedTs() {
        return failedTs;
    }

    /**
     * Returns the number of handling attempts that were made.
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
     * @return Last error or {@literal null}.
     */
    @Nullable
    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return ProcessManagerTimeoutDeadLetter.class.getSimpleName() + " [processId=" + processId + ", processType="
                + processType + ", processVersion=" + processVersion + ", retries=" + retries + "]";
    }

}
