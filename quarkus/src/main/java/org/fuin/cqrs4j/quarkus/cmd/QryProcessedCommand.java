package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.NotThreadSafe;

/**
 * Records that a command has been handled, so a re-delivered duplicate can be skipped. The primary key is the
 * command's {@code EventId} (as a string); {@code processedTs} is the epoch-millisecond time the command was
 * recorded (kept for a future retention sweep).
 */
@NotThreadSafe
@Entity
@Table(name = "QUARKUS_CMD_PROCESSED")
public class QryProcessedCommand {

    @Id
    @Column(name = "CMD_ID", nullable = false, length = 250, updatable = false)
    @NotNull
    private String commandId;

    @Column(name = "PROCESSED_TS", nullable = false)
    private long processedTs;

    /**
     * JPA constructor.
     */
    protected QryProcessedCommand() {
        super();
    }

    /**
     * Constructor with mandatory data.
     *
     * @param commandId   Unique command id (the command's {@code EventId} as a string).
     * @param processedTs Epoch millisecond time the command was recorded.
     */
    public QryProcessedCommand(@NotNull final String commandId, final long processedTs) {
        super();
        Contract.requireArgNotNull("commandId", commandId);
        this.commandId = commandId;
        this.processedTs = processedTs;
    }

    /**
     * Returns the unique command id.
     *
     * @return Command id (the {@code EventId} as a string).
     */
    @NotNull
    public String getCommandId() {
        return commandId;
    }

    /**
     * Returns the epoch millisecond time the command was recorded.
     *
     * @return Processed timestamp.
     */
    public long getProcessedTs() {
        return processedTs;
    }

    @Override
    public String toString() {
        return "QryProcessedCommand [commandId=" + commandId + ", processedTs=" + processedTs + "]";
    }

}
