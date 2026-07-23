package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandOverloadedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CommandOverloadedExceptionHandler} class.
 */
class CommandOverloadedExceptionHandlerTest {

    @Test
    void testShedCommandBecomesServiceUnavailable() {

        // PREPARE
        final CommandOverloadedExceptionHandler testee = new CommandOverloadedExceptionHandler();

        // TEST
        final ResponseEntity<String> response = testee.handle(new CommandOverloadedException("Too busy", null));

        // VERIFY: 503 and not 500 - the sender's outbox classifies a 5xx as transient and redelivers, so a
        // command that was merely turned away is never counted towards the dead-letter budget.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo("Too busy");
    }

}
