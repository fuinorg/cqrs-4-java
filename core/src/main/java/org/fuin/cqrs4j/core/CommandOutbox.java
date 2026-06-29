package org.fuin.cqrs4j.core;

import org.fuin.objects4j.common.ThreadSafe;

/**
 * Stores commands produced by a process manager for asynchronous delivery. Enqueuing is meant to
 * happen inside the process manager view's transaction so that the command is persisted atomically
 * with the process manager state.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface CommandOutbox {

    /**
     * Enqueues a command for asynchronous delivery.
     *
     * @param command Command to enqueue.
     */
    void enqueue(Command command);

}
