package org.fuin.cqrs4j.springboot.pm.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandOutbox;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads and writes the command outbox and dead-letter tables. Inserting a command
 * ({@link #enqueue(Command)}) is meant to happen inside the process manager view's transaction so that
 * the command is persisted atomically with the process manager state. Draining the queue
 * ({@link #fetchBatch(int)}, {@link #delete(String)}, {@link #recordFailure(String, String, int)}) is
 * driven by the {@link CommandQueueExecutor}.
 */
@ThreadSafe
@Repository
public class CommandOutboxService implements CommandOutbox {

    private static final String ARG_ID = "id";

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper;

    /**
     * Constructor with mandatory data.
     *
     * @param objectMapper Mapper used to serialize commands to JSON.
     */
    public CommandOutboxService(final ObjectMapper objectMapper) {
        super();
        Contract.requireArgNotNull("objectMapper", objectMapper);
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes a command and inserts it into the outbox table. Expected to be called within the
     * surrounding (process manager view) transaction.
     *
     * @param command Command to enqueue for asynchronous delivery.
     */
    @Override
    public void enqueue(final Command command) {
        Contract.requireArgNotNull("command", command);
        final String id = command.getEventId().asString();
        final String type = command.getEventType().asBaseType();
        final String json = toJson(command);
        em.persist(new ProcessManagerCommandOutbox(id, type, json, System.currentTimeMillis()));
    }

    /**
     * Reads the oldest queued commands (by creation timestamp) up to the given maximum.
     *
     * @param max Maximum number of entries to read.
     * @return Queued commands, oldest first (never {@literal null}, but may be empty).
     */
    public List<Entry> fetchBatch(final int max) {
        return em.createQuery(
                        "SELECT o FROM ProcessManagerCommandOutbox o ORDER BY o.createdTs ASC, o.id ASC", ProcessManagerCommandOutbox.class)
                .setMaxResults(max)
                .getResultList()
                .stream()
                .map(o -> new Entry(o.getId(), o.getType(), o.getJson()))
                .toList();
    }

    /**
     * Removes a successfully delivered command from the outbox.
     *
     * @param id Identifier of the command to remove.
     */
    public void delete(final String id) {
        Contract.requireArgNotNull(ARG_ID, id);
        final ProcessManagerCommandOutbox outbox = em.find(ProcessManagerCommandOutbox.class, id);
        if (outbox != null) {
            em.remove(outbox);
        }
    }

    /**
     * Records a failed delivery attempt. The retry counter is incremented; once it reaches
     * {@code maxRetries} the command is moved to the dead-letter table and removed from the outbox.
     *
     * @param id         Identifier of the command that failed to deliver.
     * @param error      Error message describing the failure.
     * @param maxRetries Maximum number of attempts before the command is dead-lettered.
     */
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

    private String toJson(final Command command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize command: " + command.getEventType().asBaseType(), ex);
        }
    }

    /**
     * Lightweight representation of a queued command, detached from the persistence context, so that
     * the HTTP delivery can happen outside the database transaction.
     *
     * @param id   Unique command identifier.
     * @param type Command type name (path variable for the command endpoint).
     * @param json Serialized command (JSON).
     */
    public record Entry(String id, String type, String json) {
    }

}
