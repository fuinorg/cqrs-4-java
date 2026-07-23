package org.fuin.cqrs4j.quarkus.test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.quarkus.pm.CommandOutboxService;
import org.fuin.cqrs4j.quarkus.test.cmd.SampleGreetCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fault-injection test for the Quarkus command outbox (O1): what happens while the command endpoint is
 * <b>unreachable</b>, as opposed to answering with a rejection.
 * <p>
 * This is the Quarkus counterpart of the Spring {@code CommandEndpointOutageIT} and pins the same deliberate
 * deviation: on breaker-open the batch is <em>deferred without recording a failure</em>. A command that was
 * never sent must not count as a failed attempt, or a short outage permanently dead-letters commands that
 * were perfectly valid.
 * <p>
 * The arrangement is what makes the deferral observable. {@code maxRetries} is 1, so every command the
 * breaker actually judged is dead-lettered at once, and more commands are queued than the breaker judges
 * before it opens ({@code requestVolumeThreshold}, 4 by default). Without the deferral every one of them
 * would be recorded as failed and dead-lettered, leaving the outbox empty.
 * <p>
 * It runs as a {@code @QuarkusTest} rather than a plain unit test because the circuit breaker needs the
 * SmallRye Fault Tolerance runtime SPI, which only exists inside the container. No event store is needed -
 * the commands are enqueued directly.
 */
@QuarkusTest
@QuarkusTestResource(MariaDbResource.class)
@QuarkusTestResource(CommandEndpointResource.class)
class CommandEndpointOutageTest {

    /** More than the breaker judges before it opens, so part of the batch is deferred rather than judged. */
    private static final int COMMAND_COUNT = 12;

    @Inject
    CommandOutboxService outboxService;

    @AfterEach
    void tearDown() {
        CommandEndpointResource.stopEndpoint();
    }

    @Test
    void anOutageDoesNotDeadLetterTheWholeBatchAndTheSurvivorsAreDeliveredOnRecovery() {

        // PREPARE: the endpoint is not listening at all - every delivery attempt is refused
        for (int i = 0; i < COMMAND_COUNT; i++) {
            enqueue("Greeting " + UUID.randomUUID());
        }

        // TEST: let the drain run into the outage
        await().atMost(60, SECONDS).until(() -> outboxService.deadLetterCount() > 0);
        await().pollDelay(10, SECONDS).atMost(15, SECONDS).untilAsserted(() ->
                assertThat(outboxService.outboxDepth()).isPositive());

        // VERIFY: the batch behind the open breaker was left untouched. Without the deviation every one of
        // these commands would have been recorded as failed on the first tick and dead-lettered.
        final long survived = outboxService.outboxDepth();
        assertThat(survived).isPositive();
        assertThat(outboxService.deadLetterCount()).isLessThan(COMMAND_COUNT);
        assertThat(CommandEndpointResource.RECEIVED_BODIES).isEmpty();

        // TEST: the endpoint comes back
        CommandEndpointResource.startEndpoint();

        // VERIFY: the breaker closes on its next probe and every survivor is delivered after all
        await().atMost(90, SECONDS).until(() -> outboxService.outboxDepth() == 0);
        assertThat(CommandEndpointResource.RECEIVED_BODIES).hasSize((int) survived);
    }

    private void enqueue(final String greeting) {
        QuarkusTransaction.requiringNew().run(() -> outboxService.enqueue(new SampleGreetCommand(greeting)));
    }

}
