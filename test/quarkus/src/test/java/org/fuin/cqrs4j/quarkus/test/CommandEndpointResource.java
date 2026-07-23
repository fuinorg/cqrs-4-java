package org.fuin.cqrs4j.quarkus.test;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Points the command outbox at a port that <b>nothing is listening on</b>, and lets a test start the endpoint
 * later. That is what makes an outage testable: the executor has to keep the queued commands while the
 * endpoint is missing and deliver them once it appears.
 * <p>
 * The port is reserved and released again during {@code start()}, so it is free but known before the
 * application is configured. The configuration has to be in place before Quarkus boots, which is why this is
 * a test resource rather than something the test method sets up.
 */
public class CommandEndpointResource implements QuarkusTestResourceLifecycleManager {

    /** Bodies the endpoint received, for the test to assert on. */
    public static final List<String> RECEIVED_BODIES = new CopyOnWriteArrayList<>();

    private static int port;

    private static HttpServer endpoint;

    @Override
    public Map<String, String> start() {
        port = reservePort();
        RECEIVED_BODIES.clear();
        return Map.of(
                "org.fuin.cqrs4j.pm.cmdqueue.url", "http://localhost:" + port,
                // One recorded failure dead-letters a command, which is what makes the deferral visible:
                // only the commands the breaker judged before it opened may be lost, never the whole batch.
                "org.fuin.cqrs4j.pm.cmdqueue.maxRetries", "1",
                "org.fuin.cqrs4j.pm.cmdqueue.batchSize", "50",
                // Fail fast against an endpoint that is not there instead of holding the drain thread.
                // Milliseconds here - unlike the Spring side, these Quarkus keys are plain integers.
                "org.fuin.cqrs4j.pm.cmdqueue.connectTimeout", "1000",
                "org.fuin.cqrs4j.pm.cmdqueue.requestTimeout", "1000",
                // Probe again quickly so the recovery half of the test does not take minutes.
                "org.fuin.cqrs4j.pm.cmdqueue.breaker.delay", "2000");
    }

    @Override
    public void stop() {
        stopEndpoint();
    }

    /**
     * Returns the port the outbox delivers to.
     *
     * @return Port of the (possibly absent) command endpoint.
     */
    public static int port() {
        return port;
    }

    /**
     * Starts the command endpoint, so deliveries begin to succeed.
     */
    public static void startEndpoint() {
        try {
            endpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            endpoint.createContext("/cmd/", exchange -> {
                RECEIVED_BODIES.add(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                final byte[] response = "{\"type\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            endpoint.start();
        } catch (final IOException ex) {
            throw new IllegalStateException("Could not start the command endpoint on port " + port, ex);
        }
    }

    /**
     * Stops the command endpoint again, if it is running.
     */
    public static void stopEndpoint() {
        if (endpoint != null) {
            endpoint.stop(0);
            endpoint = null;
        }
    }

    private static int reservePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException ex) {
            throw new IllegalStateException("Could not reserve a port for the command endpoint", ex);
        }
    }

}
