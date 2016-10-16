/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved. 
 * http://www.fuin.org/
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j;

import java.util.concurrent.Semaphore;

import javax.validation.constraints.NotNull;

import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides some helper methods.
 */
public final class Cqrs4JUtils {

    /** Classes used for JAX-B serialization. */
    public static final Class<?>[] JAXB_CLASSES = new Class<?>[] { Result.class,
            ConstraintViolationException.class };

    private static final Logger LOG = LoggerFactory.getLogger(Cqrs4JUtils.class);

    /**
     * Private by intention.
     */
    private Cqrs4JUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Tries to acquire a lock and runs the code. If no lock can be acquired,
     * the method terminates immediately without executing anything.
     * 
     * @param lock
     *            Semaphore to use.
     * @param code
     *            Code to run.
     */
    public static void tryLocked(@NotNull final Semaphore lock, @NotNull final Runnable code) {
        Contract.requireArgNotNull("lock", lock);
        Contract.requireArgNotNull("code", code);
        if (lock.tryAcquire()) {
            try {
                code.run();
            } finally {
                lock.release();
            }
        }
    }

    /**
     * Waits until a lock is available and executes the code after it was
     * acquired.
     * 
     * @param lock
     *            Semaphore to use.
     * @param code
     *            Code to run.
     */
    public static void runLocked(@NotNull final Semaphore lock, @NotNull final Runnable code) {
        Contract.requireArgNotNull("lock", lock);
        Contract.requireArgNotNull("code", code);
        try {
            lock.acquire();
            try {
                code.run();
            } finally {
                lock.release();
            }
        } catch (final InterruptedException ex) {
            LOG.warn("Couldn't clear view", ex);
        }
    }

}
