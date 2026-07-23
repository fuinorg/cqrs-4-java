package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandOverloadedException;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a shed command with <b>HTTP 503</b> instead of letting it become a 500.
 * <p>
 * The status is what makes load shedding safe end to end: the sender's outbox classifies a 5xx as a transient
 * delivery failure, so the command is deferred and redelivered rather than counted towards the dead-letter
 * budget. Using 503 rather than a plain 500 additionally tells an operator that the receiver turned the
 * command away on purpose, which is not a bug.
 * <p>
 * The advice handles exactly one exception type, which nothing but the inbound bulkhead throws, so it cannot
 * change how an application's own exceptions are reported.
 */
@ThreadSafe
@RestControllerAdvice
public class CommandOverloadedExceptionHandler {

    /**
     * Turns a shed command into a 503 response.
     *
     * @param exception Failure raised by the inbound bulkhead.
     * @return Response telling the sender to deliver the command again later.
     */
    @ExceptionHandler(CommandOverloadedException.class)
    public ResponseEntity<String> handle(final CommandOverloadedException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(exception.getMessage());
    }

}
