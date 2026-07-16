package org.fuin.cqrs4j.springboot.test.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.fuin.cqrs4j.springboot.query.core.base.EventstoreConfig;
import org.fuin.cqrs4j.springboot.test.model.PersonEntity;
import org.fuin.cqrs4j.springboot.test.model.PersonId;
import org.fuin.cqrs4j.springboot.test.model.PersonName;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ExpectedVersion;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.fuin.objects4j.jackson.ImmutableObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.fuin.cqrs4j.springboot.test.app.SpringBootTestHelper.createPersonCreatedEvent;
import static org.fuin.cqrs4j.test.helper.TestHelper.createEventstoreContainer;
import static org.fuin.cqrs4j.test.helper.TestHelper.createMariaDBContainer;

/**
 * Tests the JSON-B, JAX-B and JPA adapters.
 * <p>
 * Unfortunately Rest Assured cannot be used because it's not on "jakarta" namespace yet.
 * <a href="https://github.com/rest-assured/rest-assured/issues/1651">rest-assured issue #1651</a>
 * java.lang.NoClassDefFoundError: javax/json/bind/Jsonb
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SpringBootAdaptersIT {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBootAdaptersIT.class);

    static GenericContainer<?> es = createEventstoreContainer("26.1");

    static MariaDBContainer<?> db = createMariaDBContainer("11");

    @DynamicPropertySource
    static void elasticProperties(DynamicPropertyRegistry registry) {
        LOG.info("Eventstore Port: {}", es.getFirstMappedPort());
        registry.add(EventstoreConfig.KEY_PORT, () -> "" + es.getFirstMappedPort());
        LOG.info("Database JDBC Url: {}", db.getJdbcUrl());
        registry.add("spring.datasource.url", () -> db.getJdbcUrl());
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @BeforeAll
    static void startContainers() {
        es.start();
        db.start();
    }

    @AfterAll
    static void stopContainers() {
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

    @LocalServerPort
    private int port;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ImmutableObjectMapper.Provider mapperProvider;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/persons";
    }

    @Test
    void createAndWaitForView() {

        final PersonId id = new PersonId();
        final PersonName name = new PersonName("Peter Parker");

        // Add a created event to the aggregate stream - Should update the view
        final StreamId streamId = new SimpleStreamId(PersonId.TYPE.asString() + "-" + id.asString());
        final CommonEvent commonEvent = createPersonCreatedEvent(id, name);
        LOG.info("Append event to stream: {}", writeValueAsString(commonEvent));
        eventStore.appendToStream(streamId, ExpectedVersion.NO_OR_EMPTY_STREAM.getNo(), commonEvent);

        // Read via HTTP
        final Supplier<HttpResponse<String>> getPerson = () -> send(newBuilder(getBaseUrl() + "/" + id).GET().build());
        LOG.info("Waiting for GET person...");
        await().atMost(5, SECONDS).until(() -> getPerson.get().statusCode() == HttpStatus.OK.value());
        LOG.info("GET Response received...");

        final HttpResponse<String> read = getPerson.get();
        assertThat(read.statusCode()).isEqualTo(HttpStatus.OK.value());
        final PersonEntity copy = fromJson(read.body());
        assertThat(copy.getId()).isEqualTo(id.asBaseType());
        assertThat(copy.getName()).isEqualTo(name.asBaseType());

    }

    private static HttpRequest.Builder newBuilder(final String uri) {
        return HttpRequest.newBuilder()
                .uri(asURI(uri))
                .headers("Content-Type", MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name(),
                        "Accept", MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
    }

    private PersonEntity fromJson(final String json) {
        LOG.info("Received json: {}", json);
        return readValue(json, PersonEntity.class);
    }

    private static HttpResponse<String> send(final HttpRequest request) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return mapperProvider.reader().readValue(json, type);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to parse JSON for type " + type.getName() + ": " + json , ex);
        }
    }

    private String writeValueAsString(Object obj) {
        try {
            return mapperProvider.writer().writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to write as JSON: " + obj, ex);
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