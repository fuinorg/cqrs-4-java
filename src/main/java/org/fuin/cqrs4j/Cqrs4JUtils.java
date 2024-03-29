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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.EntityId;
import org.fuin.ddd4j.ddd.EntityIdPath;
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
            .unmodifiableList(Arrays.asList(DataResult.class, SimpleResult.class, ConstraintViolationException.class));

    private static final Logger LOG = LoggerFactory.getLogger(Cqrs4JUtils.class);

    /** Some constraints were violated. */
    public static final String PRECONDITION_VIOLATED = "PRECONDITION_VIOLATED";

    /** Result code for {@link #verifyParamEntityIdPathEqualsCmdEntityIdPath(AggregateCommand, EntityId...)} failures. */
    public static final String PARAM_ENTITY_PATH_NOT_EQUAL_CMD_ENTITY_PATH = "PARAM_ENTITY_PATH_NOT_EQUAL_CMD_ENTITY_PATH";

    /** Prefix for unique short identifiers. */
    public static final String SHORT_ID_PREFIX = "CQRS4J";

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
        return SimpleResult.error(PRECONDITION_VIOLATED, errors);
    }

    /**
     * Verifies that an aggregate identifier from a parameter is equal to the aggregate identifier from the command. This is helpful, if for
     * example the URL contains the name of the aggregate, followed by an aggregate identifier.<br>
     * Example: POST /customer/f832a5a4-dd80-49df-856a-7274de82cd6b/create<br>
     * The ID from the URL must match the aggregate ID that is passed via the command in the body.
     * 
     * @param cmd
     *            Command that has the entity identifier path to compare with.
     * @param entityIds
     *            Identifiers to compare with.
     * 
     * @return Error result or {@literal null} if validation was successful.
     * 
     * @param <ID>
     *            Type of the aggregate root identifier.
     */
    @SafeVarargs
    public static <ID extends EntityId> Result<?> verifyParamEntityIdPathEqualsCmdEntityIdPath(@NotNull final AggregateCommand<?, ?> cmd,
            @NotNull final ID... entityIds) {

        Contract.requireArgNotNull("cmd", cmd);
        Contract.requireArgNotNull("entityIds", entityIds);

        final EntityIdPath paramPath = new EntityIdPath(entityIds);
        if (paramPath.equals(cmd.getEntityIdPath())) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        for (final ID entityId : entityIds) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entityId.asTypedString());
        }

        final String msg = "Entity path constructred from URL parameters " + sb + " is not the same as command's entityPath: '"
                + cmd.getEntityIdPath() + "'";
        LOG.error(msg + " [" + PARAM_ENTITY_PATH_NOT_EQUAL_CMD_ENTITY_PATH + "]");
        return SimpleResult.error(PARAM_ENTITY_PATH_NOT_EQUAL_CMD_ENTITY_PATH, msg);

    }

}
