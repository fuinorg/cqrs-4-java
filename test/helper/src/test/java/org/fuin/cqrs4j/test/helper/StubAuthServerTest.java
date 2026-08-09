package org.fuin.cqrs4j.test.helper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the stub itself.
 * <p>
 * Worth having, and not obviously so: a stub is a test double, and a test double that quietly stops
 * checking what it claims to check is worse than none - every test using it goes on passing. The
 * assertions below are the promises the class comment makes, especially that a bad or missing PKCE
 * verifier is <em>refused</em>.
 */
class StubAuthServerTest {

    private StubAuthServer testee;

    @BeforeEach
    void setUp() {
        testee = StubAuthServer.start();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    @Test
    void testServesDiscoveryNamingItself() {

        // WHEN
        final HttpResponse<String> response = get(
                URI.create(testee.issuer() + "/.well-known/openid-configuration"));

        // THEN
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"issuer\": \"" + testee.issuer() + "\"")
                .contains("\"token_endpoint\": \"" + testee.issuer() + "/token\"")
                .contains("\"code_challenge_methods_supported\": [\"S256\"]");

    }

    @Test
    void testIssuesTokensForACodeWithAMatchingVerifier() {

        // GIVEN
        final String verifier = "a-verifier-long-enough-to-be-realistic-0123456789";
        final String code = authorize(verifier);

        // WHEN
        final HttpResponse<String> response = postToken(
                "grant_type=authorization_code&code=" + code + "&code_verifier=" + verifier);

        // THEN
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"access_token\":\"access-")
                .contains("\"refresh_token\":\"refresh-")
                .contains("\"id_token\":\"");

    }

    /**
     * The check the whole stub exists for: a client that sends the wrong verifier - or none - must be
     * refused, or a client that dropped PKCE would pass every test unchanged.
     */
    @Test
    void testRefusesACodeWhoseVerifierDoesNotMatch() {

        // GIVEN
        final String code = authorize("the-real-verifier");

        // WHEN
        final HttpResponse<String> wrong = postToken(
                "grant_type=authorization_code&code=" + code + "&code_verifier=something-else");

        // THEN
        assertThat(wrong.statusCode()).isEqualTo(400);
        assertThat(wrong.body()).contains("code_verifier does not match");

    }

    @Test
    void testRefusesAnAuthorizationRequestWithoutAChallenge() {

        // WHEN
        final HttpResponse<String> response = get(URI.create(testee.issuer()
                + "/auth?response_type=code&redirect_uri=http://127.0.0.1:1/cb&state=s"));

        // THEN
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("S256 code_challenge is required");

    }

    /**
     * And the refusal reaches the client as a redirect, so a client waiting for its callback fails
     * rather than waiting out its login timeout.
     */
    @Test
    void testTheStubBrowserTurnsARefusalIntoAnErrorCallback() {

        // GIVEN
        final CountingCallback callback = new CountingCallback();

        // WHEN
        final boolean opened = testee.visit(URI.create(testee.issuer()
                + "/auth?response_type=code&redirect_uri=" + callback.uri() + "&state=s"));

        // THEN
        assertThat(opened).isTrue();
        assertThat(callback.lastQuery()).contains("error=invalid_request").contains("state=s");
        callback.close();

    }

    @Test
    void testACodeCanBeRedeemedOnlyOnce() {

        // GIVEN
        final String verifier = "the-verifier";
        final String code = authorize(verifier);
        final String form = "grant_type=authorization_code&code=" + code + "&code_verifier=" + verifier;
        assertThat(postToken(form).statusCode()).isEqualTo(200);

        // WHEN
        final HttpResponse<String> second = postToken(form);

        // THEN
        assertThat(second.statusCode()).isEqualTo(400);
        assertThat(second.body()).contains("already redeemed");

    }

    @Test
    void testRefreshCanBeMadeToFail() {

        // GIVEN
        assertThat(postToken("grant_type=refresh_token&refresh_token=r").statusCode()).isEqualTo(200);

        // WHEN
        testee.refuseRefresh();

        // THEN
        assertThat(postToken("grant_type=refresh_token&refresh_token=r").statusCode()).isEqualTo(400);

    }

    @Test
    void testUserInfoNeedsABearerToken() {

        // WHEN
        final HttpResponse<String> without = get(URI.create(testee.issuer() + "/userinfo"));

        // THEN
        assertThat(without.statusCode()).isEqualTo(401);

    }

    @Test
    void testRecordsWhatItWasAsked() {

        // GIVEN
        authorize("the-verifier");

        // WHEN & THEN
        assertThat(testee.authorizationRequests()).singleElement().satisfies(request -> {
            assertThat(request.parameter("code_challenge_method")).contains("S256");
            assertThat(request.parameter("state")).contains("the-state");
        });
        assertThat(testee.tokenRequests()).isEmpty();

        testee.forgetRequests();
        assertThat(testee.requests()).isEmpty();

    }

    /** Starts an authorization request with a correct challenge and returns the code it produced. */
    private String authorize(final String verifier) {
        final HttpResponse<String> response = get(URI.create(testee.issuer()
                + "/auth?response_type=code&redirect_uri=http://127.0.0.1:1/cb&state=the-state"
                + "&code_challenge_method=S256&code_challenge=" + s256(verifier)));
        assertThat(response.statusCode()).isEqualTo(302);
        final String location = response.headers().firstValue("Location").orElseThrow();
        return location.replaceAll(".*[?&]code=([^&]*).*", "$1");
    }

    private HttpResponse<String> postToken(final String form) {
        final HttpClient client = HttpClient.newHttpClient();
        try {
            return client.send(HttpRequest.newBuilder(URI.create(testee.issuer() + "/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (final IOException ex) {
            throw new UncheckedIOException("Could not post to the token endpoint", ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static HttpResponse<String> get(final URI uri) {
        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            return client.send(HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (final IOException ex) {
            throw new UncheckedIOException("Could not call " + uri, ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static String s256(final String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A loopback endpoint standing in for the one a native client opens. */
    private static final class CountingCallback implements AutoCloseable {

        private final com.sun.net.httpserver.HttpServer server;

        private volatile String lastQuery = "";

        private CountingCallback() {
            try {
                server = com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
                server.createContext("/cb", exchange -> {
                    lastQuery = String.valueOf(exchange.getRequestURI().getQuery());
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
                server.start();
            } catch (final IOException ex) {
                throw new UncheckedIOException("Could not start the callback endpoint", ex);
            }
        }

        String uri() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/cb";
        }

        String lastQuery() {
            return lastQuery;
        }

        @Override
        public void close() {
            server.stop(0);
        }

    }

}
