package org.fuin.cqrs4j.test.helper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.fuin.objects4j.common.ThreadSafe;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An OpenID Connect provider, in about as much detail as a client can tell, for tests that must not
 * start a Keycloak.
 * <p>
 * A container is the right tool for provisioning questions - is the client public, does the audience
 * mapper fire, is the loopback port relaxation on. It is the wrong tool for the client's own behaviour:
 * a login is four HTTP round trips whose details (the PKCE challenge, the {@code state}, the form
 * encoding of the token request) a mocked flow would <em>assume</em> rather than exercise, and paying
 * thirty seconds of container startup to assume them is a poor trade. Against this stub the real flow,
 * the real encoders and a real loopback listener run end to end in milliseconds.
 *
 * <h2>It checks PKCE rather than merely accepting it</h2>
 * <p>
 * {@code /token} recomputes {@code S256(code_verifier)} and compares it with the challenge the
 * authorization request carried, and {@code /auth} refuses a request that carries no challenge. That is
 * what makes a flow test meaningful: a client that dropped PKCE, or that sent a {@code plain} challenge,
 * fails here instead of logging in perfectly - which is what a real authorization server would do, and
 * what no amount of asserting on the client's own code can show.
 * <p>
 * A refusal is reported as a redirect back to the client (RFC 6749 § 4.1.2.1), not merely as a 400.
 * Without that, a client that dropped PKCE sits waiting out its login timeout and the test that should
 * have caught the regression hangs instead of going red.
 *
 * <h2>What it does not do</h2>
 * <p>
 * It signs nothing. The id token is a syntactically valid JWT with a nonsense signature, which is enough
 * for a client that only ever passes it back as {@code id_token_hint} - the usual case for a native
 * application, which is a relying party for login but not a validator of anybody's tokens. A test that
 * needs verifiable signatures needs a real authorization server, not this.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * try (StubAuthServer provider = StubAuthServer.start()) {
 *     // point the client under test at provider.issuer(), and let it "open a browser" with
 *     // provider::visit - which follows the redirect exactly as a browser would
 *     ...
 *     assertThat(provider.authorizationRequests()).singleElement().satisfies(request -&gt;
 *             assertThat(request.parameter("code_challenge_method")).contains("S256"));
 * }
 * </pre>
 */
@ThreadSafe
public final class StubAuthServer implements AutoCloseable {

    /** Name the {@code /userinfo} endpoint reports for the signed-in user. */
    public static final String USER_NAME = "Demo User";

    /** Client id put into the {@code aud} of the id token when the request named none. */
    public static final String DEFAULT_CLIENT_ID = "test-client";

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * One request the provider received.
     *
     * @param method HTTP method.
     * @param path Request path.
     * @param parameters Query and form parameters, merged - the caller does not care which of the two
     *                   carried {@code client_id}, only whether it was sent.
     * @param headers Request headers, lower-cased names to their first value.
     * @param body Raw request body.
     */
    public record Recorded(String method, String path, Map<String, String> parameters,
            Map<String, String> headers, String body) {

        /**
         * Returns one query or form parameter.
         *
         * @param name Parameter name.
         *
         * @return Its value, or empty when the request did not carry it.
         */
        public Optional<String> parameter(final String name) {
            return Optional.ofNullable(parameters.get(name));
        }

        /**
         * Returns one header.
         *
         * @param name Header name, case insensitive.
         *
         * @return Its first value, or empty when the request carried none.
         */
        public Optional<String> header(final String name) {
            return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
        }
    }

    private final HttpServer server;

    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());

    /** Authorization code to the {@code code_challenge} the request was made with. */
    private final Map<String, String> pendingCodes = new ConcurrentHashMap<>();

    private final AtomicInteger issued = new AtomicInteger();

    private volatile int accessTokenLifetime = 300;

    private volatile boolean issueRefreshToken = true;

    private volatile boolean refreshRefused;

    private StubAuthServer(final HttpServer server) {
        this.server = server;
    }

    /**
     * Starts a provider on a loopback port the operating system picks.
     *
     * @return Running stub. Close it when the test is done.
     */
    public static StubAuthServer start() {
        try {
            final HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            final StubAuthServer stub = new StubAuthServer(server);
            server.createContext("/", stub::handle);
            server.start();
            return stub;
        } catch (final IOException ex) {
            throw new UncheckedIOException("Failed to start the stub authorization server", ex);
        }
    }

    /**
     * Returns the issuer to configure the client under test with.
     *
     * @return Issuer URI, on a literal loopback host - so a client that refuses to send a bearer token
     *         over plain http to anything but loopback still accepts it.
     */
    public URI issuer() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * Completes a login without a browser.
     * <p>
     * Follows the authorization URL exactly as a browser would: one request to {@code /auth}, then one
     * to whatever it redirects to - which is the loopback port the client under test opened. That
     * second request is the one the client is waiting for.
     * <p>
     * Shaped to be usable as a method reference wherever a client takes a "open this URL, tell me
     * whether you could" function.
     *
     * @param authorizationUri URL the client wanted to open.
     *
     * @return Always {@literal true} - this browser can always be opened. Pass a lambda returning
     *         {@literal false} instead to test what a client does when no browser is available.
     */
    public boolean visit(final URI authorizationUri) {
        // Not a try-with-resources: HttpClient became AutoCloseable in Java 21 and this module still
        // compiles for consumers on 17.
        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {

            final HttpResponse<String> redirect = client.send(
                    HttpRequest.newBuilder(authorizationUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            final String location = redirect.headers().firstValue("Location")
                    .orElseGet(() -> errorRedirect(authorizationUri, redirect.body()));
            client.send(HttpRequest.newBuilder(URI.create(location)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return true;

        } catch (final IOException ex) {
            throw new UncheckedIOException("The stub browser could not follow " + authorizationUri, ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while following " + authorizationUri, ex);
        }
    }

    /** Makes the token endpoint issue no refresh token, as a provider that was not asked for one does. */
    public void withoutRefreshToken() {
        issueRefreshToken = false;
    }

    /** Makes every refresh fail, as an ended session or a rotated-away refresh token does. */
    public void refuseRefresh() {
        refreshRefused = true;
    }

    /**
     * Sets how long issued access tokens are said to last.
     *
     * @param seconds Value for {@code expires_in}. A small value is how a test reaches the client's
     *                proactive renewal without waiting.
     */
    public void accessTokenLifetime(final int seconds) {
        accessTokenLifetime = seconds;
    }

    /**
     * Returns every request received, in order.
     *
     * @return Recorded requests.
     */
    public List<Recorded> requests() {
        return List.copyOf(requests);
    }

    /**
     * Returns the requests made to the token endpoint.
     *
     * @return Recorded token requests, in order.
     */
    public List<Recorded> tokenRequests() {
        return requests().stream().filter(request -> "/token".equals(request.path())).toList();
    }

    /**
     * Returns the requests made to the authorization endpoint.
     *
     * @return Recorded authorization requests, in order.
     */
    public List<Recorded> authorizationRequests() {
        return requests().stream().filter(request -> "/auth".equals(request.path())).toList();
    }

    /** Forgets the requests received so far, so a second phase can be asserted on its own. */
    public void forgetRequests() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * Turns a refusal into the redirect a real authorization server would send.
     * <p>
     * RFC 6749 § 4.1.2.1: once the redirect URI is known to be valid, an error is reported <b>to it</b>
     * rather than to the user agent. Doing the same here is not politeness - see the class comment.
     */
    private static String errorRedirect(final URI authorizationUri, final String body) {
        final Map<String, String> parameters = parse(authorizationUri.getRawQuery());
        final String description = body.replaceAll(".*\"error_description\":\"([^\"]*)\".*", "$1");
        return parameters.get("redirect_uri") + "?error=invalid_request"
                + "&error_description=" + encode(description)
                + "&state=" + encode(parameters.getOrDefault("state", ""));
    }

    private void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            final String path = exchange.getRequestURI().getPath();
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            final Map<String, String> parameters = new HashMap<>();
            parameters.putAll(parse(exchange.getRequestURI().getRawQuery()));
            parameters.putAll(parse(body));
            final Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
                }
            });
            requests.add(new Recorded(exchange.getRequestMethod(), path, Map.copyOf(parameters),
                    Map.copyOf(headers), body));

            switch (path) {
                case "/.well-known/openid-configuration" -> respond(exchange, 200, discovery());
                case "/auth" -> authorize(exchange, parameters);
                case "/token" -> token(exchange, parameters);
                case "/userinfo" -> userInfo(exchange);
                case "/logout" -> respond(exchange, 200, "{}");
                case "/jwks" -> respond(exchange, 200, "{\"keys\":[]}");
                default -> respond(exchange, 404, "{}");
            }
        }
    }

    private String discovery() {
        return """
                {
                  "issuer": "%1$s",
                  "authorization_endpoint": "%1$s/auth",
                  "token_endpoint": "%1$s/token",
                  "userinfo_endpoint": "%1$s/userinfo",
                  "end_session_endpoint": "%1$s/logout",
                  "jwks_uri": "%1$s/jwks",
                  "response_types_supported": ["code"],
                  "subject_types_supported": ["public"],
                  "id_token_signing_alg_values_supported": ["RS256"],
                  "code_challenge_methods_supported": ["S256"],
                  "grant_types_supported": ["authorization_code", "refresh_token"]
                }
                """.formatted(issuer());
    }

    private void authorize(final HttpExchange exchange, final Map<String, String> parameters)
            throws IOException {

        final String challenge = parameters.get("code_challenge");
        if (challenge == null || !"S256".equals(parameters.get("code_challenge_method"))) {
            // A real authorization server configured for a public client refuses exactly this.
            respond(exchange, 400, error("invalid_request", "an S256 code_challenge is required"));
            return;
        }

        final String code = "code-" + issued.incrementAndGet();
        pendingCodes.put(code, challenge);

        exchange.getResponseHeaders().add("Location", parameters.get("redirect_uri")
                + "?code=" + encode(code)
                + "&state=" + encode(parameters.getOrDefault("state", "")));
        exchange.sendResponseHeaders(302, -1);
    }

    private void token(final HttpExchange exchange, final Map<String, String> parameters)
            throws IOException {

        final String grantType = parameters.get("grant_type");
        final String clientId = parameters.getOrDefault("client_id", DEFAULT_CLIENT_ID);

        if ("refresh_token".equals(grantType)) {
            if (refreshRefused) {
                respond(exchange, 400, error("invalid_grant", "the session has ended"));
            } else {
                respond(exchange, 200, tokens(clientId));
            }
            return;
        }

        if (!"authorization_code".equals(grantType)) {
            respond(exchange, 400, error("unsupported_grant_type", grantType + " is not enabled"));
            return;
        }

        final String challenge = pendingCodes.remove(parameters.get("code"));
        if (challenge == null) {
            respond(exchange, 400, error("invalid_grant", "unknown or already redeemed code"));
            return;
        }
        final String verifier = parameters.get("code_verifier");
        if (verifier == null || !challenge.equals(s256(verifier))) {
            respond(exchange, 400, error("invalid_grant", "the code_verifier does not match"));
            return;
        }
        respond(exchange, 200, tokens(clientId));
    }

    private void userInfo(final HttpExchange exchange) throws IOException {
        final String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            respond(exchange, 401, error("invalid_token", "no bearer token"));
            return;
        }
        respond(exchange, 200,
                "{\"sub\":\"demo\",\"name\":\"" + USER_NAME + "\",\"preferred_username\":\"demo\"}");
    }

    private String tokens(final String clientId) {
        final int serial = issued.incrementAndGet();
        final StringBuilder json = new StringBuilder("{\"token_type\":\"Bearer\"")
                .append(",\"access_token\":\"access-").append(serial).append('"')
                .append(",\"expires_in\":").append(accessTokenLifetime)
                .append(",\"id_token\":\"").append(idToken(clientId)).append('"');
        if (issueRefreshToken) {
            json.append(",\"refresh_token\":\"refresh-").append(serial).append('"');
        }
        return json.append('}').toString();
    }

    /**
     * A JWT that parses and means nothing.
     * <p>
     * Well formed because client libraries parse the {@code id_token} while reading the token response;
     * unsigned in any meaningful sense because nothing here or in a native client verifies one.
     */
    private String idToken(final String clientId) {
        final String header = BASE64URL.encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        final String payload = BASE64URL.encodeToString(("{\"sub\":\"demo\",\"iss\":\"" + issuer()
                + "\",\"aud\":\"" + clientId + "\",\"exp\":4102444800}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "."
                + BASE64URL.encodeToString("not-a-signature".getBytes(StandardCharsets.UTF_8));
    }

    private static String error(final String code, final String description) {
        return "{\"error\":\"" + code + "\",\"error_description\":\"" + description + "\"}";
    }

    private static String s256(final String verifier) {
        try {
            return BASE64URL.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static Map<String, String> parse(final String encoded) {
        final Map<String, String> parameters = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return parameters;
        }
        for (final String pair : encoded.split("&")) {
            final int equals = pair.indexOf('=');
            if (equals > 0) {
                parameters.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
        }
        return parameters;
    }

    private static String decode(final String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void respond(final HttpExchange exchange, final int status, final String json)
            throws IOException {
        final byte[] out = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(out);
        }
    }

}
