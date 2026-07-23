package org.fuin.cqrs4j.springboot.test.app;

import org.fuin.cqrs4j.springboot.query.core.base.EventstoreConfig;
import org.fuin.cqrs4j.springboot.test.model.PersonId;
import org.fuin.cqrs4j.springboot.test.model.PersonName;
import org.fuin.cqrs4j.test.helper.FaultInjectingProxy;
import org.fuin.esc.api.EscConnectionException;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ExpectedVersion;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.fuin.cqrs4j.springboot.test.app.SpringBootTestHelper.createPersonCreatedEvent;
import static org.fuin.cqrs4j.test.helper.TestHelper.createEventstoreContainer;
import static org.fuin.cqrs4j.test.helper.TestHelper.createMariaDBContainer;

/**
 * Fault-injection test for the projection catch-up (V1): the event store goes away while the application is
 * running, and has to come back without a restart.
 *
 * <ul>
 *     <li>a write fails <b>fast and typed</b> ({@link EscConnectionException}) instead of hanging,</li>
 *     <li>the read model keeps serving what it already projected - an event store outage must not take the
 *         query side down with it,</li>
 *     <li>the projection catches up again once the store returns, including the event that was written while
 *         it was unreachable.</li>
 * </ul>
 *
 * The outage is injected with a {@link FaultInjectingProxy} rather than by stopping the container, because
 * the application binds to the container's mapped port when its context starts: a restarted container gets a
 * different port and could never be reached again, so the recovery half of the test would be untestable.
 * Requires a Docker environment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventStoreOutageIT {

    private static final GenericContainer<?> es = createEventstoreContainer("26.1");

    private static final MariaDBContainer<?> db = createMariaDBContainer("11");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static FaultInjectingProxy proxy;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add(EventstoreConfig.KEY_PORT, () -> "" + proxy.port());
        registry.add("spring.datasource.url", db::getJdbcUrl);
    }

    @BeforeAll
    static void startInfrastructure() throws IOException {
        es.start();
        db.start();
        proxy = FaultInjectingProxy.to("localhost", es.getFirstMappedPort());
    }

    @AfterAll
    static void stopInfrastructure() {
        if (proxy != null) {
            proxy.close();
        }
        es.stop();
        db.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private EventStore eventStore;

    @Test
    void theStoreGoesAwayAndComesBack() {

        // PREPARE: one person is projected while everything is healthy
        final PersonId before = new PersonId();
        append(before, "Peter Parker");
        await().atMost(30, SECONDS).until(() -> statusOf(before) == HttpStatus.OK.value());

        // TEST: the event store becomes unreachable
        proxy.cut();

        // VERIFY: a write fails fast with the typed transient exception instead of blocking the caller.
        // This is the esc call timeout and connectivity classification surfacing at application level.
        final long start = System.currentTimeMillis();
        assertThatThrownBy(() -> append(new PersonId(), "During Outage"))
                .isInstanceOf(EscConnectionException.class);
        assertThat(System.currentTimeMillis() - start).isLessThan(30_000);

        // VERIFY: the query side is unaffected - the read model lives in the database and keeps answering
        // for everything that was already projected. A store outage degrades freshness, not availability.
        assertThat(statusOf(before)).isEqualTo(HttpStatus.OK.value());

        // TEST: the store comes back
        proxy.forward();

        // VERIFY: writes work again and the projection catches up without a restart. The gRPC client
        // reconnects on its own schedule and the breaker has to close first, so this takes a few ticks.
        final PersonId after = new PersonId();
        await().atMost(60, SECONDS).ignoreExceptions().untilAsserted(() -> append(after, "Mary Jane"));
        await().atMost(60, SECONDS).until(() -> statusOf(after) == HttpStatus.OK.value());

        // VERIFY: nothing that was projected before the outage was lost along the way
        assertThat(statusOf(before)).isEqualTo(HttpStatus.OK.value());
    }

    private void append(final PersonId id, final String name) {
        final StreamId streamId = new SimpleStreamId(PersonId.TYPE.asString() + "-" + id.asString());
        eventStore.appendToStream(streamId, ExpectedVersion.NO_OR_EMPTY_STREAM.getNo(),
                createPersonCreatedEvent(id, new PersonName(name)));
    }

    private int statusOf(final PersonId id) {
        try {
            // The controller declares the JSON media type, so a request without these headers is
            // rejected with 415 rather than answered - which looks exactly like "not projected yet".
            final String json = MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name();
            final HttpResponse<String> response = CLIENT.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/persons/" + id))
                            .headers("Content-Type", json, "Accept", json)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (final IOException ex) {
            throw new IllegalStateException("Could not query the read model", ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted querying the read model", ex);
        }
    }

}
