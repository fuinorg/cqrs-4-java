package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.fuin.cqrs4j.core.CommandDeliveryException;
import org.fuin.cqrs4j.core.TransientCommandDeliveryException;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
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

    // Package visible so a test can inject a mock; httpClient() honours a pre-set instance.
    @Nullable
    volatile HttpClient httpClient;

    /**
     * Returns the HTTP client, created lazily because the configuration is injected after construction.
     * Without a connect timeout a delivery to an unreachable host blocks the drain thread until the OS
     * gives up, which can be minutes.
     *
     * @return HTTP client.
     */
    HttpClient httpClient() {
        HttpClient result = httpClient;
        if (result == null) {
            synchronized (this) {
                result = httpClient;
                if (result == null) {
                    result = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                            .build();
                    httpClient = result;
                }
            }
        }
        return result;
    }

    /**
     * Sends a command for execution.
     *
     * @param type        Unique type name of the command (path variable).
     * @param contentType Full content type the command is serialized with (base type, encoding and version),
     *                    sent verbatim as the {@code Content-Type} header so the receiver deserializes and
     *                    up-casts by the exact media type.
     * @param cmdJson     Serialized command (request body).
     * @return Result returned by the command endpoint.
     */
    public String cmd(final String type, final String contentType, final String cmdJson) {
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("contentType", contentType);
        Contract.requireArgNotNull("cmdJson", cmdJson);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getUrl() + "/cmd/" + type))
                .header("Content-Type", contentType)
                .timeout(Duration.ofMillis(config.getRequestTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(cmdJson));
        applyAuthHeaders(builder);
        try {
            final HttpResponse<String> response = httpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
            final int status = response.statusCode();
            if (status >= 500) {
                // The endpoint is there but cannot handle the request right now - worth delivering again.
                throw new TransientCommandDeliveryException(
                        "Command delivery failed: " + status + " " + response.body(), status, null);
            }
            if (status >= 300) {
                // The endpoint answered that the command itself is the problem - delivering it again
                // cannot succeed, so it must not consume the retry budget as if it were an outage.
                throw new CommandDeliveryException(
                        "Command delivery failed: " + status + " " + response.body(), status, null);
            }
            return response.body();
        } catch (final HttpTimeoutException ex) {
            throw new TransientCommandDeliveryException(
                    "Command delivery timed out: " + ex.getMessage(), 0, ex);
        } catch (final IOException ex) {
            throw new TransientCommandDeliveryException(
                    "Could not reach the command endpoint: " + ex.getMessage(), 0, ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TransientCommandDeliveryException("Interrupted while delivering command", 0, ex);
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
