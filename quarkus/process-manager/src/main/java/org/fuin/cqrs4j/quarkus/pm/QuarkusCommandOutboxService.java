package org.fuin.cqrs4j.quarkus.pm;
import org.fuin.cqrs4j.jpa.pm.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandOutbox;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.jsonb.JsonbProvider;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Reads and writes the command outbox and dead-letter tables. Inserting a command ({@link #enqueue(Command)}) is
 * meant to happen inside the process manager view's transaction so that the command is persisted atomically with
 * the process manager state. Draining the queue ({@link #fetchBatch(int)}, {@link #delete(String)},
 * {@link #recordFailure(String, String, int)}) is driven by the {@link QuarkusCommandQueueExecutor}, and each of
 * those runs in its own {@code REQUIRES_NEW} transaction so one failing command does not roll back its siblings.
 */
@ThreadSafe
@ApplicationScoped
public class QuarkusCommandOutboxService implements CommandOutbox {

    private static final String ARG_ID = "id";

    @Inject
    EntityManager em;

    @Inject
    JsonbProvider jsonbProvider;

    /**
     * Serializes a command and inserts it into the outbox table. Expected to be called within the surrounding
     * (process manager view) transaction, so it does not open its own.
     *
     * @param command Command to enqueue for asynchronous delivery.
     */
    @Override
    public void enqueue(final Command command) {
        Contract.requireArgNotNull("command", command);
        final String id = command.getEventId().asString();
        final String type = command.getEventType().asBaseType();
        final String version = command.getVersion();
        final String json = jsonbProvider.jsonb().toJson(command);
        em.persist(new ProcessManagerCommandOutbox(id, type, version, json, System.currentTimeMillis()));
    }

    /**
     * Reads the oldest queued commands (by creation timestamp) up to the given maximum.
     *
     * @param max Maximum number of entries to read.
     * @return Queued commands, oldest first (never {@literal null}, but may be empty).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<Entry> fetchBatch(final int max) {
        return em.createQuery(
                        "SELECT o FROM ProcessManagerCommandOutbox o ORDER BY o.createdTs ASC, o.id ASC", ProcessManagerCommandOutbox.class)
                .setMaxResults(max)
                .getResultList()
                .stream()
                .map(o -> new Entry(o.getId(), o.getType(), o.getVersion(), o.getJson()))
                .toList();
    }

    /**
     * Removes a successfully delivered command from the outbox.
     *
     * @param id Identifier of the command to remove.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void delete(final String id) {
        Contract.requireArgNotNull(ARG_ID, id);
        final ProcessManagerCommandOutbox outbox = em.find(ProcessManagerCommandOutbox.class, id);
        if (outbox != null) {
            em.remove(outbox);
        }
    }

    /**
     * Records a failed delivery attempt. The retry counter is incremented; once it reaches {@code maxRetries} the
     * command is moved to the dead-letter table and removed from the outbox.
     *
     * @param id         Identifier of the command that failed to deliver.
     * @param error      Error message describing the failure.
     * @param maxRetries Maximum number of attempts before the command is dead-lettered.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailure(final String id, @Nullable final String error, final int maxRetries) {
        Contract.requireArgNotNull(ARG_ID, id);
        final ProcessManagerCommandOutbox outbox = em.find(ProcessManagerCommandOutbox.class, id);
        if (outbox == null) {
            return;
        }
        outbox.recordFailure(error);
        if (outbox.getRetries() >= maxRetries) {
            em.persist(ProcessManagerCommandDeadLetter.fromOutbox(outbox, System.currentTimeMillis()));
            em.remove(outbox);
        }
    }

    /**
     * Returns the number of commands currently waiting in the outbox (outbox depth). Runs in its own transaction
     * so it can be called from outside a transaction (e.g. a metrics gauge).
     *
     * @return Current outbox row count.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long outboxDepth() {
        return em.createQuery("SELECT COUNT(o) FROM ProcessManagerCommandOutbox o", Long.class).getSingleResult();
    }

    /**
     * Returns the number of commands that exhausted their retries and were moved to the dead-letter table. Runs in
     * its own transaction so it can be called from outside a transaction.
     *
     * @return Current dead-letter row count.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long deadLetterCount() {
        return em.createQuery("SELECT COUNT(d) FROM ProcessManagerCommandDeadLetter d", Long.class).getSingleResult();
    }

    /**
     * Lightweight representation of a queued command, detached from the persistence context, so that the HTTP
     * delivery can happen outside the database transaction.
     *
     * @param id      Unique command identifier.
     * @param type    Command type name (path variable for the command endpoint).
     * @param version Schema version the command is serialized at ({@literal null} if unversioned).
     * @param json    Serialized command (JSON).
     */
    public record Entry(String id, String type, @Nullable String version, String json) {
    }

}
