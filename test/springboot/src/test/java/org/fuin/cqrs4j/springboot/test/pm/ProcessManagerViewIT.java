package org.fuin.cqrs4j.springboot.test.pm;

import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.fuin.cqrs4j.springboot.pm.core.CommandOutboxService;
import org.fuin.cqrs4j.springboot.pm.core.ProcessManagerConfig;
import org.fuin.cqrs4j.jpa.pm.ProcessManagerCommandDeadLetter;
import org.fuin.cqrs4j.jpa.pm.ProcessManagerCommandOutbox;
import org.fuin.cqrs4j.springboot.query.core.base.EventstoreConfig;
import org.fuin.cqrs4j.springboot.test.app.SpringBootApp;
import org.fuin.cqrs4j.springboot.test.model.PersonId;
import org.fuin.cqrs4j.springboot.test.model.PersonName;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ExpectedVersion;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.fuin.cqrs4j.springboot.test.app.SpringBootTestHelper.createPersonCreatedEvent;
import static org.fuin.cqrs4j.test.helper.TestHelper.createEventstoreContainer;
import static org.fuin.cqrs4j.test.helper.TestHelper.createMariaDBContainer;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * End-to-end tests for {@link SampleProcessManagerView}: an event appended to the (esgrpc) event store
 * drives the process manager view, which records its state and enqueues a command into the outbox -
 * both in MariaDB, within one transaction. The command queue executor then delivers the command over
 * HTTP to a stubbed command endpoint. Requires a Docker environment.
 * <p>
 * The view engine and queue executor poll continuously against one shared context, so each test uses a
 * unique person name and scopes any injected failure (handling failure / delivery failure) to that
 * name, keeping the tests independent of each other and of execution order.
 */
@SpringBootTest(classes = {SpringBootApp.class, ProcessManagerViewIT.PmTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProcessManagerViewIT {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessManagerViewIT.class);

    static GenericContainer<?> es = createEventstoreContainer("26.1");

    static MariaDBContainer<?> db = createMariaDBContainer("11");

    /** Bodies of all command requests the endpoint received (across retries). */
    private static final List<String> RECEIVED_BODIES = new CopyOnWriteArrayList<>();

    /** Command endpoint responds with HTTP 500 when a request body contains one of these names. */
    private static final Set<String> FAIL_DELIVERY_FOR_NAMES = new CopyOnWriteArraySet<>();

    private static HttpServer commandEndpoint;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add(EventstoreConfig.KEY_PORT, () -> "" + es.getFirstMappedPort());
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.url",
                () -> "http://localhost:" + commandEndpoint.getAddress().getPort());
        // Dead-letter after a couple of attempts so the failure test stays fast.
        registry.add("org.fuin.cqrs4j.pm.cmdqueue.maxRetries", () -> "2");
    }

    @BeforeAll
    static void startInfrastructure() throws IOException {
        commandEndpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        commandEndpoint.createContext("/cmd/", exchange -> {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            RECEIVED_BODIES.add(body);
            final boolean fail = FAIL_DELIVERY_FOR_NAMES.stream().anyMatch(body::contains);
            final byte[] response = (fail ? "{\"type\":\"ERROR\"}" : "{\"type\":\"OK\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(fail ? 500 : 200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        commandEndpoint.start();
        es.start();
        db.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        commandEndpoint.stop(0);
        try {
            es.stop();
        } catch (final RuntimeException ex) {
            LOG.error("Failed to stop eventstore", ex);
        }
        try {
            db.stop();
        } catch (final RuntimeException ex) {
            LOG.error("Failed to stop database", ex);
        }
    }

    @Autowired
    private EventStore eventStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    @Test
    void happyPath_commandIsDeliveredAndStateRecorded() {
        // PREPARE
        final PersonId id = new PersonId();
        final String name = uniqueName();

        // TEST
        append(id, name);

        // VERIFY - the process manager reacted (state persisted) and the command was delivered.
        await().atMost(60, SECONDS).until(() -> deliveredFor(name));
        assertThat(stateCount(id.asBaseType())).isEqualTo(1L);
    }

    @Test
    void failingHandling_recordsNothing_andEventIsReadAgain() {
        // PREPARE - the view will throw while handling the event for this name
        final PersonId id = new PersonId();
        final String name = uniqueName();
        SampleProcessManagerView.FAIL_HANDLING_FOR_NAMES.add(name);

        // TEST
        append(id, name);

        // VERIFY - while handling keeps failing, the transaction rolls back: no state, no command,
        // and (because the checkpoint is not advanced) the event is read again on every poll.
        await().pollDelay(4, SECONDS).atMost(8, SECONDS).untilAsserted(() -> {
            assertThat(stateCount(id.asBaseType())).isZero();
            assertThat(deliveredFor(name)).isFalse();
        });

        // Once the failure is resolved, the re-read of the same event is processed successfully.
        SampleProcessManagerView.FAIL_HANDLING_FOR_NAMES.remove(name);
        await().atMost(60, SECONDS).untilAsserted(() -> {
            assertThat(stateCount(id.asBaseType())).isEqualTo(1L);
            assertThat(deliveredFor(name)).isTrue();
        });
    }

    @Test
    void repeatedDeliveryFailure_movesCommandToDeadLetter() {
        // PREPARE - the command endpoint always rejects this command
        final PersonId id = new PersonId();
        final String name = uniqueName();
        FAIL_DELIVERY_FOR_NAMES.add(name);

        // TEST
        append(id, name);

        // VERIFY - after exhausting the retries the command lands in the dead-letter table and is gone
        // from the outbox; the process manager state itself was recorded (handling succeeded).
        await().atMost(60, SECONDS).until(() -> deadLetterContains(name));
        assertThat(outboxContains(name)).isFalse();
        assertThat(stateCount(id.asBaseType())).isEqualTo(1L);
    }

    private void append(final PersonId id, final String name) {
        final StreamId streamId = new SimpleStreamId(PersonId.TYPE.asString() + "-" + id.asString());
        eventStore.appendToStream(streamId, ExpectedVersion.NO_OR_EMPTY_STREAM.getNo(),
                createPersonCreatedEvent(id, new PersonName(name)));
    }

    private static String uniqueName() {
        return "Person " + UUID.randomUUID();
    }

    private static boolean deliveredFor(final String name) {
        return RECEIVED_BODIES.stream().anyMatch(body -> body.contains(name));
    }

    private long stateCount(final UUID id) {
        final Long count = inTransaction(() ->
                em.createQuery("SELECT COUNT(s) FROM SAMPLE_PM_STATE s WHERE s.id = :id", Long.class)
                        .setParameter("id", id)
                        .getSingleResult());
        return count == null ? 0 : count;
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
     * Wires the process manager support and registers the sample process manager view (prototype scope,
     * as required by the view engine) and an authentication provider.
     */
    @Configuration
    @Import(ProcessManagerConfig.class)
    @EntityScan(basePackages = "org.fuin.cqrs4j.springboot.test.pm")
    static class PmTestConfig {

        @Bean(SampleProcessManagerView.BEAN_NAME)
        @Scope(SCOPE_PROTOTYPE)
        SampleProcessManagerView sampleProcessManagerView(final CommandOutboxService outboxService,
                                                          final EntityManager em) {
            return new SampleProcessManagerView(outboxService, em);
        }

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
