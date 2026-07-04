package org.fuin.cqrs4j.quarkus.cmd;
import org.fuin.cqrs4j.jpa.command.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Relational {@link ProcessedCommandStore} backed by the {@code QUARKUS_CMD_PROCESSED} table
 * ({@link QryProcessedCommand}). A record is written only after the command handler has succeeded, so
 * re-delivered duplicates are recognized by {@link #processed(String)} and skipped. Tenant isolation is
 * inherited from the surrounding (routing) datasource, exactly like the projection checkpoint and lease.
 */
@ThreadSafe
@ApplicationScoped
public class QuarkusProcessedCommandStore implements ProcessedCommandStore {

    private static final String ARG_COMMAND_ID = "commandId";

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public boolean processed(@NotNull final String commandId) {
        Contract.requireArgNotNull(ARG_COMMAND_ID, commandId);
        return em.find(QryProcessedCommand.class, commandId) != null;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
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
