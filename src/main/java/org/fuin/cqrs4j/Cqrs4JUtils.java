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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.AggregateRootId;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides some helper methods.
 */
public final class Cqrs4JUtils {

    /** Classes used for JAX-B serialization. */
    public static final List<Class<?>> JAXB_CLASSES = Collections
            .unmodifiableList(Arrays.asList(Result.class, ConstraintViolationException.class));

    private static final Logger LOG = LoggerFactory.getLogger(Cqrs4JUtils.class);

    /** Some constraints were violated. */
    public static final String PRECONDITION_VIOLATED = "PRECONDITION_VIOLATED";

    /** Result code for {@link #verifyParamIdEqualsCmdAggregateId(AggregateRootId, AggregateCommand)} failures. */
    public static final String PARAM_ID_NOT_EQUAL_CMD_AGGREGATE_ID = "PARAM_ID_NOT_EQUAL_CMD_AGGREGATE_ID";

    /**
     * Private by intention.
     */
    private Cqrs4JUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Tries to acquire a lock and runs the code. If no lock can be acquired, the method terminates immediately without executing anything.
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
     * Waits until a lock is available and executes the code after it was acquired.
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
        } catch (final InterruptedException ex) { // NOSONAR
            LOG.warn("Couldn't clear view", ex);
        }
    }

    /**
     * Verifies a precondition. In case of constraint violations, the error is logged and an error result is returned.
     * 
     * @param validator
     *            Validator to use.
     * @param obj
     *            Object to validate. A {@literal null} value is considered to be an error.
     * 
     * @return Error result or {@literal null} if validation was successful.
     */
    public static Result<?> verifyPrecondition(@NotNull final Validator validator, @NotNull final Object obj) {

        Contract.requireArgNotNull("validator", validator);
        Contract.requireArgNotNull("obj", obj);

        final Set<ConstraintViolation<Object>> violations = validator.validate(obj);
        if (violations.isEmpty()) {
            return null;
        }
        final String errors = Contract.asString(violations, ", ");
        LOG.error(errors);
        return Result.error(PRECONDITION_VIOLATED, errors);
    }

    /**
     * Verifies that an aggregate identifier from a parameter is equal to the aggregate identifier from the command. This is helpful, if for
     * example the URL contains the name of the aggregate, followed by an aggregate identifier.<br>
     * Example: POST /customer/f832a5a4-dd80-49df-856a-7274de82cd6b/create<br>
     * The ID from the URL must match the aggregate ID that is passed via the command in the body.
     * 
     * @param aggregateRootId
     *            Identifier to compare with.
     * @param cmd
     *            Command that has the aggregate ID to compare with.
     * 
     * @return Error result or {@literal null} if validation was successful.
     * 
     * @param <ID>
     *            Type of the aggregate root identifier.
     */
    public static <ID extends AggregateRootId> Result<?> verifyParamIdEqualsCmdAggregateId(@NotNull final ID aggregateRootId,
            @NotNull final AggregateCommand<ID> cmd) {

        Contract.requireArgNotNull("aggregateRootId", aggregateRootId);
        Contract.requireArgNotNull("cmd", cmd);

        if (aggregateRootId.equals(cmd.getAggregateRootId())) {
            return null;
        }
        final String msg = "URL parameter ID '" + aggregateRootId + "' is not the same as command's aggregate root ID: '"
                + cmd.getAggregateRootId() + "'";
        LOG.error(msg + " [" + PARAM_ID_NOT_EQUAL_CMD_AGGREGATE_ID + "]");
        return Result.error(PARAM_ID_NOT_EQUAL_CMD_AGGREGATE_ID, msg);

    }

}
