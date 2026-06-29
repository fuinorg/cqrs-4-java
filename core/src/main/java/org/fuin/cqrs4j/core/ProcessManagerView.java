package org.fuin.cqrs4j.core;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * A transactional-outbox process manager: a normal {@link View} that, while handling events, updates
 * its own state and enqueues the commands it wants to send into the outbox - both within the view's
 * transaction, so they commit or roll back together. Implementations expose their {@link CommandOutbox}
 * via {@link #getCommandOutboxService()} and use {@link #send(Command)} from within
 * {@link #handleEvents(java.util.List)} to enqueue commands.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface ProcessManagerView extends View {

    /**
     * Returns the outbox used to enqueue the commands produced by this process manager.
     *
     * @return Command outbox.
     */
    CommandOutbox getCommandOutboxService();

    /**
     * Enqueues a command for asynchronous delivery. Must be called from within
     * {@link #handleEvents(java.util.List)} so that the command is persisted in the same transaction as
     * the process manager's state change.
     *
     * @param command Command to send.
     */
    default void send(final Command command) {
        Contract.requireArgNotNull("command", command);
        getCommandOutboxService().enqueue(command);
    }

}
