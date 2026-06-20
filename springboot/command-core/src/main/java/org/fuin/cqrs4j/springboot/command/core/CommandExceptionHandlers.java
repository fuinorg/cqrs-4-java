/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.springboot.command.core;

import jakarta.servlet.http.HttpServletRequest;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.Result;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.cqrs4j.core.ToResultCapable;
import org.fuin.cqrs4j.jackson.SimpleResult;
import org.fuin.ddd4j.core.*;
import org.fuin.cqrs4j.core.CommandHandlerClassNotFoundException;
import org.fuin.cqrs4j.core.DuplicateNameException;
import org.fuin.ddd4j.core.UnauthorizedException;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.ExceptionShortIdentifable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Translates exceptions thrown while processing a command into an appropriate {@link Result} and
 * HTTP status code. Registered as a Spring {@link ControllerAdvice} so it applies to all controllers.
 */
@ControllerAdvice
public class CommandExceptionHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(CommandExceptionHandlers.class);

    /**
     * Fallback handler for any otherwise unhandled exception. Re-throws exceptions that carry their
     * own {@link ResponseStatus} so Spring can handle them, otherwise returns an error result.
     *
     * @param req Current request.
     * @param ex  Exception that occurred.
     * @return Error result describing the failure.
     * @throws Exception Re-thrown if the exception is annotated with {@link ResponseStatus}.
     */
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public Result defaultErrorHandler(HttpServletRequest req, Exception ex) throws Exception {
        LOG.error("Failed to process request (default error handler)", ex);
        if (AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class) != null) {
            throw ex;
        }
        if (ex instanceof ToResultCapable trc) {
            return trc.toResult();
        }
        return new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage());
    }

    /**
     * Handles the case where no command handler class could be found for a command.
     *
     * @param req Current request.
     * @param ex  Exception that occurred.
     * @return Error result describing the failure.
     */
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(CommandHandlerClassNotFoundException.class)
    @ResponseBody
    public SimpleResult handleCommandHandlerClassNotFound(HttpServletRequest req, Exception ex) {
        LOG.error("Failed to process request (class not found)", ex);
        return new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage());
    }

    /**
     * Handles an unauthorized command execution and maps it to HTTP status {@code 403 Forbidden}.
     *
     * @param req Current request.
     * @param ex  Exception that occurred.
     * @return Error result describing the failure.
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseBody
    public SimpleResult handleForbidden(HttpServletRequest req, Exception ex) {
        final String code = code(ex);
        LOG.error("Failed to process request (forbidden): {}", ex.getMessage());
        return new SimpleResult(ResultType.ERROR, code, ex.getMessage());
    }

    /**
     * Handles an attempt to use a name that already exists in another entity.
     *
     * @param req Current request.
     * @param ex  Exception that occurred.
     * @return Error result describing the failure.
     */
    @ResponseStatus
    @ExceptionHandler(DuplicateNameException.class)
    @ResponseBody
    public SimpleResult handleDuplicateName(HttpServletRequest req, Exception ex) {
        LOG.info("Failed to process request (duplicate name): {}", ex.getMessage());
        return new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage());
    }

    /**
     * Handles a validation failure of the command and maps it to HTTP status {@code 400 Bad Request}.
     *
     * @param ex Exception that occurred.
     * @return Error result describing the failure.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseBody
    public SimpleResult handleConstraintViolation(Exception ex) {
        LOG.info("Failed to process request (constraint violation): {}", ex.getMessage());
        return new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage());
    }

    /**
     * Handles a failed command execution by inspecting the cause and mapping the various aggregate
     * related failures to a fitting HTTP status code (for example "not found" or "conflict").
     *
     * @param cef Exception wrapping the actual cause of the failure.
     * @return Response entity with an error result and the appropriate HTTP status.
     */
    @ExceptionHandler(CommandExecutionFailedException.class)
    public ResponseEntity<Result<?>> handleCommandExecutionFailed(CommandExecutionFailedException cef) {
        final Throwable rawCause = cef.getCause();
        final Exception cause = (rawCause instanceof Exception ex) ? ex : cef;
        if (cause instanceof AggregateNotFoundException ex) {
            LOG.error("Aggregate not found: {}", ex.getMessage());
            return new ResponseEntity<>(new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage()), HttpStatus.NOT_FOUND);
        }
        if (cause instanceof AggregateVersionNotFoundException ex) {
            LOG.error("Aggregate version not found: {}", ex.getMessage());
            return new ResponseEntity<>(new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage()), HttpStatus.NOT_FOUND);
        }
        if (cause instanceof AggregateVersionConflictException ex) {
            LOG.error("Aggregate version conflict: {}", ex.getMessage());
            return new ResponseEntity<>(new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage()), HttpStatus.CONFLICT);
        }
        if (cause instanceof AggregateDeletedException ex) {
            LOG.error("Aggregate deleted: {}", ex.getMessage());
            return new ResponseEntity<>(new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage()), HttpStatus.CONFLICT);
        }
        if (cause instanceof AggregateAlreadyExistsException ex) {
            LOG.error("Aggregate already exists: {}", ex.getMessage());
            return new ResponseEntity<>(new SimpleResult(ResultType.ERROR, code(ex), ex.getMessage()), HttpStatus.CONFLICT);
        }
        if (cause instanceof ToResultCapable trc) {
            LOG.error("Failed to process request (" + ToResultCapable.class.getSimpleName() + ")", cause);
            return new ResponseEntity<>(trc.toResult(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        LOG.error("Failed to process request", cause);
        return new ResponseEntity<>(new SimpleResult(ResultType.ERROR, code(cause), cause.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static String code(Exception ex) {
        if (ex instanceof ExceptionShortIdentifable esi) {
            return esi.getShortId();
        }
        return ex.getClass().getSimpleName();
    }

}
