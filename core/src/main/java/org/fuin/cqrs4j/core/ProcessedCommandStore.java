package org.fuin.cqrs4j.core;

import jakarta.validation.constraints.NotNull;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Records the identifiers of commands that have already been handled so that an at-least-once command
 * delivery (see {@link CommandOutbox}) becomes <em>effectively-once</em> on the receiver side: a re-delivered
 * command whose id is already present is skipped instead of being handled a second time.
 * <p>
 * The contract is <b>record-after-success</b>: the receiver checks {@link #processed(String)} first and only
 * calls {@link #markProcessed(String)} once the command handler has completed successfully. Because the
 * handler's side effect (an event-store append) and this store are separate resources that cannot share a
 * single transaction, a crash between the append and the record may still re-run a command - that residual
 * window is covered by the aggregate's expected-version check. The store therefore provides effectively-once,
 * not exactly-once, semantics.
 * <p>
 * The command id is the globally unique {@code EventId} that travels with the command, so no tenant
 * qualifier is required; tenant isolation is inherited from the surrounding (routing) datasource, exactly like
 * the projection checkpoint and lease.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface ProcessedCommandStore {

    /**
     * Determines whether a command with the given id has already been handled.
     *
     * @param commandId Unique command id (the command's {@code EventId} as a string).
     * @return {@literal true} if the command was already processed and should be skipped.
     */
    boolean processed(@NotNull String commandId);

    /**
     * Records that a command with the given id has been handled successfully. Recording the same id more than
     * once is a no-op (idempotent), so a concurrent re-delivery cannot fail the receiver.
     *
     * @param commandId Unique command id (the command's {@code EventId} as a string).
     */
    void markProcessed(@NotNull String commandId);

}
