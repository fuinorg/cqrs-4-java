package org.fuin.cqrs4j.quarkus.test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fuin.cqrs4j.jsonb.JsonbRegistry;
import org.fuin.cqrs4j.quarkus.test.model.PersonEntity;
import org.fuin.cqrs4j.quarkus.test.model.PersonId;
import org.fuin.cqrs4j.quarkus.test.model.PersonName;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ExpectedVersion;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.jsonb.JsonbProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.fuin.cqrs4j.quarkus.test.QuarkusTestHelper.commonEvent;
import static org.fuin.cqrs4j.quarkus.test.QuarkusTestHelper.personCreatedEvent;

/**
 * Tests the JSON-B, JAX-B and JPA adapters.
 * <p>
 * Unfortunately Rest Assured cannot be used because it's not on "jakarta" namespace yet.
 * <a href="https://github.com/rest-assured/rest-assured/issues/1651">rest-assured issue #1651</a>
 * java.lang.NoClassDefFoundError: javax/json/bind/Jsonb
 */
@Disabled("Find out why connection to Eventstore hangs (See TODO below)...")
@QuarkusTest
@QuarkusTestResource(MariaDbResource.class)
@QuarkusTestResource(EventstoreResource.class)
class QuarkusAppTest {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusAppTest.class);

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Inject
    EventStore eventStore;

    @Inject
    JsonbProvider jsonbProvider;

    @ConfigProperty(name = "quarkus.http.port")
    Integer port;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/persons";
    }

    @Test
    void createAndWaitForView() {

        final PersonId id = new PersonId();
        final PersonName name = new PersonName("Peter Parker");

        // Add a created event to the aggregate stream - Should update the view
        final StreamId streamId = new SimpleStreamId(PersonId.TYPE.asString() + "-" + id.asString());
        final CommonEvent commonEvent = commonEvent(personCreatedEvent(id, name));

        LOG.info("Append event to stream: {}", jsonbProvider.jsonb().toJson(commonEvent));
        // TODO Investigate why Quarkus hangs here!
        eventStore.appendToStream(streamId, ExpectedVersion.NO_OR_EMPTY_STREAM.getNo(), commonEvent);

        // Read via HTTP
        final Supplier<HttpResponse<String>> getPerson = () -> send(newBuilder(getBaseUrl() + "/" + id).GET().build());

        LOG.info("Waiting for GET person...");
        await().atMost(5, SECONDS).until(() -> getPerson.get().statusCode() == Response.Status.OK.getStatusCode());
        LOG.info("GET Response received...");

        final HttpResponse<String> read = getPerson.get();
        assertThat(read.statusCode()).isEqualTo(Response.Status.OK.getStatusCode());
        final PersonEntity copy = fromJson(read.body());
        assertThat(copy.getId()).isEqualTo(id);
        assertThat(copy.getName()).isEqualTo(name);

    }

    private static HttpRequest.Builder newBuilder(final String uri) {
        return HttpRequest.newBuilder()
                .uri(asURI(uri))
                .headers("Content-Type", MediaType.APPLICATION_JSON + ";charset=" + StandardCharsets.UTF_8.name(),
                        "Accept", MediaType.APPLICATION_JSON + ";charset=" + StandardCharsets.UTF_8.name());
    }

    private PersonEntity fromJson(final String json) {
        LOG.info("Received json: {}", json);
        return jsonbProvider.jsonb().fromJson(json, PersonEntity.class);
    }

    private static HttpResponse<String> send(final HttpRequest request) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static URI asURI(String uri) {
        try {
            return new URI(uri);
        } catch (final URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

}