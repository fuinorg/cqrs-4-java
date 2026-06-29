package org.fuin.cqrs4j.springboot.pm.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.utils4j.TestOmitted;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the transactional-outbox flow against a real database (MariaDB via
 * Testcontainers) and a real HTTP command endpoint (a lightweight JDK {@link HttpServer}). It verifies
 * that an enqueued command is persisted in the outbox, delivered by the {@link CommandQueueExecutor}
 * (with authentication headers applied) and removed on success, and moved to the dead-letter table
 * once delivery fails durably.
 * <p>
 * Runs in the {@code integration-test} phase (Maven Failsafe) and requires a Docker environment.
 */
@TestOmitted("Integration test (Testcontainers) - exercises the other classes end-to-end")
@SpringBootTest(classes = ProcessManagerOutboxIntegrationIT.TestApp.class)
@Testcontainers
class ProcessManagerOutboxIntegrationIT {

    @Container
    @SuppressWarnings("resource")
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static final List<RecordedRequest> RECEIVED = new CopyOnWriteArrayList<>();

    private static volatile int responseStatus = 200;

    private static HttpServer commandEndpoint;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.url",
                () -> "http://localhost:" + commandEndpoint.getAddress().getPort());
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.cron", () -> "-"); // disabled - drained manually
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.maxRetries", () -> "2");
    }

    @BeforeAll
    static void startCommandEndpoint() throws IOException {
        commandEndpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        commandEndpoint.createContext("/cmd/", exchange -> {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            RECEIVED.add(new RecordedRequest(exchange.getRequestURI().getPath(), body,
                    exchange.getRequestHeaders().getFirst("X-Test-Auth")));
            final byte[] response = "{\"type\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        commandEndpoint.start();
    }

    @AfterAll
    static void stopCommandEndpoint() {
        commandEndpoint.stop(0);
    }

    @Autowired
    private CommandOutboxService outboxService;

    @Autowired
    private CommandQueueExecutor executor;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void resetState() {
        RECEIVED.clear();
        responseStatus = 200;
        // Use JPQL (entity names) so the test does not depend on the physical table naming.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.createQuery("DELETE FROM " + ProcessManagerCommandOutbox.class.getSimpleName()).executeUpdate();
            em.createQuery("DELETE FROM " + ProcessManagerCommandDeadLetter.class.getSimpleName()).executeUpdate();
        });
    }

    @Test
    void successfulDeliveryIsRemovedFromOutbox() {
        // PREPARE
        responseStatus = 200;
        enqueueInTransaction(new TestCommand("hello"));
        assertThat(outboxCount()).isEqualTo(1);

        // TEST
        executor.drain();

        // VERIFY - command was POSTed (with auth header) and the outbox row removed
        assertThat(RECEIVED).hasSize(1);
        assertThat(RECEIVED.get(0).path()).isEqualTo("/cmd/TestCommand");
        assertThat(RECEIVED.get(0).body()).contains("hello");
        assertThat(RECEIVED.get(0).auth()).isEqualTo("secret");
        assertThat(outboxCount()).isZero();
        assertThat(deadLetterCount()).isZero();
    }

    @Test
    void durableFailureMovesToDeadLetter() {
        // PREPARE - endpoint always fails
        responseStatus = 500;
        enqueueInTransaction(new TestCommand("boom"));
        assertThat(outboxCount()).isEqualTo(1);

        // TEST - first attempt: stays in the outbox with an incremented retry counter
        executor.drain();
        assertThat(outboxCount()).isEqualTo(1);
        assertThat(deadLetterCount()).isZero();

        // TEST - second attempt reaches maxRetries (2) -> moved to dead-letter
        executor.drain();

        // VERIFY
        assertThat(outboxCount()).isZero();
        assertThat(deadLetterCount()).isEqualTo(1);
    }

    private void enqueueInTransaction(final Command command) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> outboxService.enqueue(command));
    }

    private long outboxCount() {
        return count(ProcessManagerCommandOutbox.class);
    }

    private long deadLetterCount() {
        return count(ProcessManagerCommandDeadLetter.class);
    }

    private long count(final Class<?> entityClass) {
        final Long count = new TransactionTemplate(transactionManager).execute(status ->
                em.createQuery("SELECT COUNT(o) FROM " + entityClass.getSimpleName() + " o", Long.class).getSingleResult());
        return count == null ? 0 : count;
    }

    /**
     * Minimal test application: enables auto-configuration (DataSource / JPA / Jackson), imports the
     * production configuration and supplies a {@link CommandAuthProvider} that adds an auth header.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProcessManagerConfig.class)
    static class TestApp {

        @Bean
        CommandAuthProvider testAuthProvider() {
            return headers -> {
                final Map<String, List<String>> map = new LinkedHashMap<>(headers.map());
                map.put("X-Test-Auth", List.of("secret"));
                return HttpHeaders.of(map, (name, value) -> true);
            };
        }

    }

    /**
     * A command request received by the test command endpoint.
     *
     * @param path Request path.
     * @param body Request body (the serialized command).
     * @param auth Value of the {@code X-Test-Auth} header (or {@literal null}).
     */
    record RecordedRequest(String path, String body, @Nullable String auth) {
    }

    /**
     * Minimal {@link Command} implementation used to exercise the outbox.
     */
    static final class TestCommand implements Command {

        private final EventId eventId = new EventId();

        private final String payload;

        TestCommand(final String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }

        @Override
        @JsonIgnore
        public EventId getEventId() {
            return eventId;
        }

        @Override
        @JsonIgnore
        public EventType getEventType() {
            return new EventType("TestCommand");
        }

        @Override
        @JsonIgnore
        public ZonedDateTime getEventTimestamp() {
            return ZonedDateTime.parse("2020-01-01T00:00:00Z");
        }

        @Override
        @JsonIgnore
        @Nullable
        public EventId getCorrelationId() {
            return null;
        }

        @Override
        @JsonIgnore
        @Nullable
        public EventId getCausationId() {
            return null;
        }

    }

}
