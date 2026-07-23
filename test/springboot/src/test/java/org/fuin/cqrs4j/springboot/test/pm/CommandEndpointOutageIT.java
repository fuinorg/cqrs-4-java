package org.fuin.cqrs4j.springboot.test.pm;

import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.fuin.cqrs4j.jpa.pm.ProcessManagerCommandDeadLetter;
import org.fuin.cqrs4j.jpa.pm.ProcessManagerCommandOutbox;
import org.fuin.cqrs4j.springboot.pm.core.CommandOutboxService;
import org.fuin.cqrs4j.springboot.pm.core.ProcessManagerConfig;
import org.fuin.cqrs4j.springboot.test.app.SpringBootApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.fuin.cqrs4j.test.helper.TestHelper.createMariaDBContainer;

/**
 * Fault-injection test for the command outbox: what happens while the command endpoint is <b>unreachable</b>,
 * as opposed to answering with a rejection.
 * <p>
 * This is the end-to-end counterpart of the deliberate deviation in O1/O2 - on breaker-open the batch is
 * <em>deferred without recording a failure</em>. The distinction only shows up in an outage: a command that
 * was never sent must not count as a failed attempt, or a short outage permanently dead-letters commands that
 * were perfectly valid. {@code ProcessManagerViewIT} covers the opposite case (the endpoint answers and
 * rejects, which does consume the budget and does dead-letter), and the unit tests cover the breaker in
 * isolation; only this test shows that the two combine correctly against a real database and a real HTTP
 * client.
 * <p>
 * {@code maxRetries} is set high enough that the circuit breaker opens before the retry budget could run out.
 * With the value used by {@code ProcessManagerViewIT} the command would dead-letter first and the deferral
 * would never come into play - which is exactly the failure this test is here to catch.
 * <p>
 * No event store is needed: the command is enqueued directly. Requires a Docker environment.
 */
@SpringBootTest(classes = {SpringBootApp.class, CommandEndpointOutageIT.OutageTestConfig.class})
@Testcontainers
class CommandEndpointOutageIT {

    private static final MariaDBContainer<?> db = createMariaDBContainer("12.3");

    private static final List<String> RECEIVED_BODIES = new CopyOnWriteArrayList<>();

    private static HttpServer commandEndpoint;

    private static int endpointPort;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.url", () -> "http://localhost:" + endpointPort);
        // One recorded failure dead-letters a command. That is what makes the deferral observable: only
        // the commands the breaker judged before it opened may be lost, never the rest of the batch.
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.maxRetries", () -> "1");
        // Short timeouts so an unreachable endpoint fails fast instead of holding the drain thread.
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.connectTimeout", () -> "1s");
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.requestTimeout", () -> "1s");
        // Probe again quickly so the recovery half of the test does not take minutes.
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.breaker.initialWait", () -> "2s");
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.breaker.maxWait", () -> "5s");
    }

    @BeforeAll
    static void startInfrastructure() throws IOException {
        // Bind a port, then release it again: the endpoint is *down* for the first half of the test, and
        // the executor must keep the command instead of giving up on it.
        endpointPort = reservePort();
        db.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        stopEndpoint();
        db.stop();
    }

    @Autowired
    private CommandOutboxService outboxService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    /** More than the breaker judges before it opens, so part of the batch is deferred rather than judged. */
    private static final int COMMAND_COUNT = 12;

    @Test
    void anOutageDoesNotDeadLetterTheWholeBatchAndTheSurvivorsAreDeliveredOnRecovery() {

        // PREPARE: the endpoint is not listening at all - every delivery attempt is refused
        final List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < COMMAND_COUNT; i++) {
            final String name = "Person " + UUID.randomUUID();
            names.add(name);
            enqueue(name);
        }

        // TEST: let the drain run into the outage. maxRetries is 1, so every command the breaker actually
        // judged is dead-lettered at once; the batch behind the open breaker must be left untouched.
        await().atMost(60, SECONDS).until(() -> deadLetterCount() > 0);
        await().pollDelay(10, SECONDS).atMost(15, SECONDS).untilAsserted(() ->
                assertThat(outboxDepth()).isPositive());

        // VERIFY: this is the whole point of the deviation - without it every one of these commands would
        // have been recorded as failed on the first tick and dead-lettered, with none left to deliver.
        final long survived = outboxDepth();
        assertThat(survived).isPositive();
        assertThat(deadLetterCount()).isLessThan(COMMAND_COUNT);
        assertThat(RECEIVED_BODIES).isEmpty();

        // TEST: the endpoint comes back
        startEndpointQuietly();

        // VERIFY: the breaker closes on its next probe and every survivor is delivered after all
        await().atMost(90, SECONDS).until(() -> outboxDepth() == 0);
        assertThat(RECEIVED_BODIES).hasSize((int) survived);
        assertThat(names.stream().filter(CommandEndpointOutageIT::wasDelivered).count()).isEqualTo(survived);
    }

    private static boolean wasDelivered(final String name) {
        return RECEIVED_BODIES.stream().anyMatch(body -> body.contains(name));
    }

    private long outboxDepth() {
        return inTransaction(() -> em.createQuery("SELECT COUNT(o) FROM "
                + ProcessManagerCommandOutbox.class.getSimpleName() + " o", Long.class).getSingleResult());
    }

    private long deadLetterCount() {
        return inTransaction(() -> em.createQuery("SELECT COUNT(d) FROM "
                + ProcessManagerCommandDeadLetter.class.getSimpleName() + " d", Long.class).getSingleResult());
    }

    private static void startEndpointQuietly() {
        try {
            startEndpoint();
        } catch (final IOException ex) {
            throw new IllegalStateException("Could not restart the command endpoint", ex);
        }
    }

    private void enqueue(final String name) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                outboxService.enqueue(new SampleNotifyCommand(name)));
    }

    private static int reservePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void startEndpoint() throws IOException {
        commandEndpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", endpointPort), 0);
        commandEndpoint.createContext("/cmd/", exchange -> {
            RECEIVED_BODIES.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            final byte[] response = "{\"type\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        commandEndpoint.start();
    }

    private static void stopEndpoint() {
        if (commandEndpoint != null) {
            commandEndpoint.stop(0);
            commandEndpoint = null;
        }
    }

    private boolean deadLetterContains(final String name) {
        return inTransaction(() ->
                em.createQuery("SELECT d FROM " + ProcessManagerCommandDeadLetter.class.getSimpleName() + " d",
                                ProcessManagerCommandDeadLetter.class)
                        .getResultList().stream().anyMatch(d -> d.getJson().contains(name)));
    }

    private boolean outboxContains(final String name) {
        return inTransaction(() ->
                em.createQuery("SELECT o FROM " + ProcessManagerCommandOutbox.class.getSimpleName() + " o",
                                ProcessManagerCommandOutbox.class)
                        .getResultList().stream().anyMatch(o -> o.getJson().contains(name)));
    }

    private <T> T inTransaction(final java.util.function.Supplier<T> supplier) {
        return new TransactionTemplate(transactionManager).execute(status -> supplier.get());
    }

    /**
     * Wires the process manager support and an authentication provider. No process manager view is
     * registered - this test enqueues its command directly.
     */
    @Configuration
    @Import(ProcessManagerConfig.class)
    @EntityScan(basePackages = "org.fuin.cqrs4j.springboot.test.pm")
    static class OutageTestConfig {

        @Bean
        CommandAuthProvider commandAuthProvider() {
            return headers -> {
                final Map<String, List<String>> map = new LinkedHashMap<>(headers.map());
                map.put("X-Test-Auth", List.of("secret"));
                return HttpHeaders.of(map, (name, value) -> true);
            };
        }

    }

}
