package org.fuin.cqrs4j.springboot.command.core;
import org.fuin.cqrs4j.jpa.command.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relational {@link ProcessedCommandStore} backed by the {@code SPRING_CMD_PROCESSED} table
 * ({@link QryProcessedCommand}). A record is written in its own {@code REQUIRES_NEW} transaction only after the
 * command handler has succeeded, so re-delivered duplicates are recognized by {@link #processed(String)} and
 * skipped. Tenant isolation is inherited from the surrounding (routing) datasource, exactly like the projection
 * checkpoint and lease.
 */
@ThreadSafe
@Repository
public class QryProcessedCommandStore implements ProcessedCommandStore {

    private static final String ARG_COMMAND_ID = "commandId";

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public boolean processed(@NotNull final String commandId) {
        Contract.requireArgNotNull(ARG_COMMAND_ID, commandId);
        return em.find(QryProcessedCommand.class, commandId) != null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(@NotNull final String commandId) {
        Contract.requireArgNotNull(ARG_COMMAND_ID, commandId);
        // Recording the same id twice must be a no-op (idempotent), so guard against an existing row.
        if (em.find(QryProcessedCommand.class, commandId) == null) {
            em.persist(new QryProcessedCommand(commandId, now()));
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
