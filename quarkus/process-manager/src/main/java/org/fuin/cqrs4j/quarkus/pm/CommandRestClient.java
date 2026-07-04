package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Sends a serialized command to a command endpoint ({@code POST /cmd/{type}} with the JSON command as request
 * body), the Quarkus counterpart of the Spring {@code CommandRestClient}. It uses the JDK
 * {@link java.net.http.HttpClient} directly - which pairs naturally with the {@link CommandAuthProvider} SPI
 * (whose {@code create} works on {@link HttpHeaders}) - so no MicroProfile REST Client or header bridging is
 * needed. When exactly one {@link CommandAuthProvider} bean is present its headers are added to each request.
 */
@ThreadSafe
@ApplicationScoped
public class CommandRestClient {

    @Inject
    CommandQueueConfig config;

    @Inject
    Instance<CommandAuthProvider> authProviders;

    HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Sends a command for execution.
     *
     * @param type    Unique type name of the command (path variable).
     * @param cmdJson Serialized command (JSON request body).
     * @return Result returned by the command endpoint (JSON).
     */
    public String cmd(final String type, final String cmdJson) {
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("cmdJson", cmdJson);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getUrl() + "/cmd/" + type))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cmdJson));
        applyAuthHeaders(builder);
        try {
            final HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Command delivery failed: " + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (final IOException ex) {
            throw new IllegalStateException("Command delivery failed: " + ex.getMessage(), ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delivering command", ex);
        }
    }

    private void applyAuthHeaders(final HttpRequest.Builder builder) {
        if (!authProviders.isResolvable()) {
            return;
        }
        final HttpHeaders base = HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (name, value) -> true);
        final HttpHeaders withAuth = authProviders.get().create(base);
        withAuth.map().forEach((name, values) -> {
            if (!"Content-Type".equalsIgnoreCase(name)) {
                values.forEach(value -> builder.header(name, value));
            }
        });
    }

}
