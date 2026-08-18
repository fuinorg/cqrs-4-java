/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */

package org.fuin.cqrs4j.test.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Provisions a realm in a running Keycloak and hands out tokens from it.
 *
 * <h2>What it is for</h2>
 * <p>
 * The negative cases of a resource server can only be proven against something that mints real tokens:
 * that no token is refused, that a token for <b>another audience</b> is refused, and that a correct one
 * is accepted. A stub cannot show the second - it is the signature and the {@code aud} claim of a real
 * issuer that make the test mean anything - which is why {@link StubAuthServer} covers the login flow
 * and this covers the resource server.
 * <p>
 * Two clients are created for exactly that contrast: {@link #CLIENT_WITH_AUDIENCE} carries the audience
 * mapper, {@link #CLIENT_WITHOUT_AUDIENCE} does not. Both are service accounts, so
 * {@link #tokenFor(String)} is a {@code client_credentials} grant and needs no user, no browser and no
 * password.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * &#64;Container
 * static final GenericContainer&lt;?&gt; KEYCLOAK = TestHelper.createKeycloakContainer("26.0.7");
 *
 * &#64;DynamicPropertySource
 * static void properties(DynamicPropertyRegistry registry) {
 *     registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -&gt; realm.issuerUri());
 * }
 *
 * &#64;BeforeAll
 * static void provision() {
 *     realm = new KeycloakRealm(url(KEYCLOAK), "acme");
 *     realm.provision();
 * }
 * </pre>
 * <p>
 * Keycloak in {@code start-dev} derives its issuer from the request it receives, so a randomly mapped
 * container port needs no further configuration - the URL used here and the {@code iss} it mints agree
 * by construction. A fixed host-port binding is not necessary.
 * <p>
 * <h2>Tokens that belong to a person</h2>
 * <p>
 * A service account proves everything a resource server does on its own, and nothing that depends on
 * <b>who</b> is calling: an application that resolves the token's subject to a record of its own - a
 * person, their roles, what those roles allow - cannot be tested with a client that has no person behind
 * it. {@link #createPasswordGrantClient(String)}, {@link #setPassword(String, String)} and
 * {@link #tokenForUser(String, String, String)} close that gap, and they are meant to be used together
 * with whatever the application under test does to create the login in the first place.
 * <p>
 * <b>The password grant is a test fixture and nothing else.</b> Real clients of these applications use
 * Authorization Code with PKCE and leave {@code directAccessGrantsEnabled} off deliberately; the client
 * created here is a separate one that exists only inside a test realm, so turning it on cannot weaken
 * anything a deployment ships.
 * <p>
 * Deliberately built on {@code java.net.http} and Jackson rather than Spring's {@code RestClient}: a
 * Quarkus application needs this fixture just as much, and it should not have to put Spring on its test
 * class path to get it.
 */
public final class KeycloakRealm {

    /** Client whose tokens carry the audience the resource server validates. */
    public static final String CLIENT_WITH_AUDIENCE = "test-client";

    /** Client whose tokens are perfectly valid and meant for somebody else. */
    public static final String CLIENT_WITHOUT_AUDIENCE = "test-client-no-audience";

    /** Audience put into the first client's tokens. */
    public static final String AUDIENCE = "test-api";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;

    private final String realm;

    private final String audience;

    /**
     * Constructor using the default audience.
     *
     * @param baseUrl Base URL of the running Keycloak, without a trailing slash.
     * @param realm Realm to create. A tenant realm, never Keycloak's own {@code master}.
     */
    public KeycloakRealm(final String baseUrl, final String realm) {
        this(baseUrl, realm, AUDIENCE);
    }

    /**
     * Constructor with an explicit audience, for an application that validates its own.
     *
     * @param baseUrl Base URL of the running Keycloak, without a trailing slash.
     * @param realm Realm to create.
     * @param audience Value the audience mapper emits, matching the application's
     *                 {@code spring.security.oauth2.resourceserver.jwt.audiences}.
     */
    public KeycloakRealm(final String baseUrl, final String realm, final String audience) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl==null").replaceAll("/+$", "");
        this.realm = Objects.requireNonNull(realm, "realm==null");
        this.audience = Objects.requireNonNull(audience, "audience==null");
    }

    /**
     * Creates the realm and the two clients. Safe to call once per test class.
     */
    public void provision() {
        final String admin = adminToken();
        createRealm(admin);
        createServiceAccountClient(admin, CLIENT_WITH_AUDIENCE, true);
        createServiceAccountClient(admin, CLIENT_WITHOUT_AUDIENCE, false);
    }

    /**
     * Creates one more service account client, for a test that needs a third contrast - a caller that
     * has the audience but not a role, say.
     *
     * @param clientId Client to create.
     * @param withAudienceMapper Whether its tokens carry the audience.
     */
    public void createServiceAccountClient(final String clientId, final boolean withAudienceMapper) {
        createServiceAccountClient(adminToken(), clientId, withAudienceMapper);
    }

    /**
     * Grants a realm role to a client's service account, through a group.
     * <p>
     * Through a group and never directly, because that is the rule the applications using this follow -
     * and because only <b>realm</b> roles reach the token in a form
     * {@code KeycloakJwtAuthenticationConverter} can see. A client role is invisible to it, which is a
     * silent 403 rather than an error.
     *
     * @param clientId Client whose service account gets the role.
     * @param role Realm role, created if it does not exist.
     */
    public void grantRole(final String clientId, final String role) {
        final String admin = adminToken();
        createRealmRole(admin, role);
        final String group = role + "s";
        final String groupId = createGroup(admin, group);
        assignRoleToGroup(admin, groupId, role);
        addServiceAccountToGroup(admin, clientId, groupId);
    }

    /**
     * Grants a client's service account a role of another client, such as Keycloak's own
     * {@code realm-management}.
     * <p>
     * Directly rather than through a group, which is the opposite of {@link #grantRole(String, String)}
     * and for the same underlying reason: that one goes through a group because only realm roles reach
     * the token in a form a resource server's converter can read, and a group is how an application
     * grants them. A client role is never read from the token at all - Keycloak checks it itself when
     * the admin API is called - so there is nothing to be gained by the indirection here.
     * <p>
     * The case this exists for is an application that administers its own realm under the rights of
     * whoever is signed in, rather than with a credential of its own. Such an application can only be
     * tested if the caller it is given actually holds those rights.
     *
     * @param clientId Client whose service account gets the roles.
     * @param ofClient Client the roles belong to, for instance {@code realm-management}.
     * @param roles Roles to grant, which must already exist on that client.
     */
    public void grantClientRole(final String clientId, final String ofClient, final String... roles) {
        final String admin = adminToken();
        final String ofClientUuid = clientUuid(admin, ofClient);
        final JsonNode available = read(adminGet(admin, "/clients/" + ofClientUuid + "/roles"));
        final ArrayNode granted = JSON.createArrayNode();
        for (final String role : roles) {
            granted.add(roleNamed(available, role, ofClient));
        }
        final String uuid = clientUuid(admin, clientId);
        final JsonNode user = read(adminGet(admin, "/clients/" + uuid + "/service-account-user"));
        adminPost(admin, "/users/" + user.path("id").asText() + "/role-mappings/clients/" + ofClientUuid,
                granted);
    }

    /**
     * Creates a public client that can exchange a username and password for a token.
     * <p>
     * See the class javadoc: this is how a test obtains a token that belongs to a <b>person</b>, and it
     * is not how anything in production obtains one.
     *
     * @param clientId Client to create. Its tokens carry the audience, because a token that cannot reach
     *                 the application under test is of no use to the test.
     */
    public void createPasswordGrantClient(final String clientId) {
        final ObjectNode representation = JSON.createObjectNode()
                .put("clientId", clientId)
                .put("enabled", true)
                .put("publicClient", true)
                .put("standardFlowEnabled", false)
                .put("directAccessGrantsEnabled", true)
                .put("serviceAccountsEnabled", false);
        representation.putArray("protocolMappers").add(audienceMapper());
        adminPost(adminToken(), "/clients", representation);
    }

    /**
     * Sets a password on an existing user, so that a test can sign in as them.
     * <p>
     * Separate from creating the user on purpose: the interesting case is a login the <b>application
     * under test</b> created, through whatever domain operation it offers, and a test that created the
     * user itself would have proven nothing about that. A Keycloak account made this way normally has no
     * credential at all - the person sets their own through a one-time link - which is exactly why this
     * step is needed.
     * <p>
     * The pending required actions are cleared along with it, and that is not incidental: an account
     * invited to choose its own password carries {@code UPDATE_PASSWORD}, and possibly
     * {@code VERIFY_EMAIL}, until somebody follows that link. Keycloak refuses a token for an account
     * with either of them outstanding - {@code "Account is not fully set up"} - so setting a password
     * without clearing them produces a user who still cannot sign in. Following the link is what a real
     * person does; this is the test's stand-in for having done it.
     *
     * @param username Username of the account, which is what the application chose it to be.
     * @param password Password to set, permanently rather than as one to be changed on first use.
     */
    public void setPassword(final String username, final String password) {
        final String admin = adminToken();
        final String id = userId(admin, username);
        final ObjectNode credential = JSON.createObjectNode()
                .put("type", "password")
                .put("value", password)
                .put("temporary", false);
        adminPut(admin, "/users/" + id + "/reset-password", credential);
        final ObjectNode settled = JSON.createObjectNode()
                .put("enabled", true)
                .put("emailVerified", true);
        settled.putArray("requiredActions");
        adminPut(admin, "/users/" + id, settled);
    }

    /**
     * Obtains an access token for a person, through the password grant.
     *
     * @param clientId Client created by {@link #createPasswordGrantClient(String)}.
     * @param username Username of the person.
     * @param password Password set by {@link #setPassword(String, String)}.
     *
     * @return Encoded access token, whose {@code sub} is the person's own.
     */
    public String tokenForUser(final String clientId, final String username, final String password) {
        final String form = "grant_type=password"
                + "&client_id=" + encode(clientId)
                + "&username=" + encode(username)
                + "&password=" + encode(password);
        return read(post(tokenUri(), form, null)).path("access_token").asText();
    }

    /**
     * Returns the issuer of the provisioned realm.
     *
     * @return Value for {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
     */
    public String issuerUri() {
        return baseUrl + "/realms/" + realm;
    }

    /**
     * Returns the realm's token endpoint.
     *
     * @return Token URI.
     */
    public String tokenUri() {
        return issuerUri() + "/protocol/openid-connect/token";
    }

    /**
     * Obtains an access token for one of the clients.
     *
     * @param clientId Client to authenticate as - one of the two constants, or another created by hand.
     *
     * @return Encoded access token.
     */
    public String tokenFor(final String clientId) {
        final String form = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(secretOf(adminToken(), clientId));
        final JsonNode body = read(post(tokenUri(), form, null));
        return body.path("access_token").asText();
    }

    private void createRealm(final String admin) {
        final ObjectNode representation = JSON.createObjectNode()
                .put("realm", realm)
                .put("enabled", true);
        adminPost(admin, "", representation);
    }

    private void createServiceAccountClient(final String admin, final String clientId,
            final boolean withAudienceMapper) {

        final ObjectNode representation = JSON.createObjectNode()
                .put("clientId", clientId)
                .put("enabled", true)
                .put("publicClient", false)
                .put("standardFlowEnabled", false)
                .put("directAccessGrantsEnabled", false)
                .put("serviceAccountsEnabled", true);

        if (withAudienceMapper) {
            representation.putArray("protocolMappers").add(audienceMapper());
        }

        adminPost(admin, "/clients", representation);
    }

    /**
     * The mapper that puts the audience into a client's tokens.
     * <p>
     * Without it the token carries only Keycloak's default 'account' audience, and a resource server that
     * validates an audience rejects it - which is the whole point of the contrast between the two service
     * account clients.
     *
     * @return Protocol mapper representation.
     */
    private ObjectNode audienceMapper() {
        final ObjectNode mapper = JSON.createObjectNode()
                .put("name", "audience")
                .put("protocol", "openid-connect")
                .put("protocolMapper", "oidc-audience-mapper");
        mapper.putObject("config")
                .put("included.custom.audience", audience)
                .put("access.token.claim", "true")
                .put("id.token.claim", "false");
        return mapper;
    }

    private static JsonNode roleNamed(final JsonNode available, final String role, final String ofClient) {
        for (final JsonNode candidate : available) {
            if (role.equals(candidate.path("name").asText())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Client '" + ofClient + "' has no role '" + role + "'");
    }

    private String userId(final String admin, final String username) {
        final JsonNode users = read(adminGet(admin, "/users?exact=true&username=" + encode(username)));
        if (users.isEmpty()) {
            // Worth naming the account rather than failing on an empty array later: the username is
            // chosen by the application under test, so a mismatch here is a wrong assumption in the test
            // about what it called the login.
            throw new IllegalStateException("No user '" + username + "' in realm '" + realm + "'");
        }
        return users.get(0).path("id").asText();
    }

    private void createRealmRole(final String admin, final String role) {
        adminPost(admin, "/roles", JSON.createObjectNode().put("name", role));
    }

    private String createGroup(final String admin, final String group) {
        adminPost(admin, "/groups", JSON.createObjectNode().put("name", group));
        final JsonNode groups = read(adminGet(admin, "/groups?search=" + encode(group)));
        for (final JsonNode candidate : groups) {
            if (group.equals(candidate.path("name").asText())) {
                return candidate.path("id").asText();
            }
        }
        throw new IllegalStateException("Group '" + group + "' was not created");
    }

    private void assignRoleToGroup(final String admin, final String groupId, final String role) {
        final JsonNode representation = read(adminGet(admin, "/roles/" + encode(role)));
        final ArrayNode roles = JSON.createArrayNode();
        roles.add(representation);
        adminPost(admin, "/groups/" + groupId + "/role-mappings/realm", roles);
    }

    private void addServiceAccountToGroup(final String admin, final String clientId, final String groupId) {
        final String uuid = clientUuid(admin, clientId);
        final JsonNode user = read(adminGet(admin, "/clients/" + uuid + "/service-account-user"));
        adminPut(admin, "/users/" + user.path("id").asText() + "/groups/" + groupId);
    }

    private String secretOf(final String admin, final String clientId) {
        final JsonNode secret = read(
                adminGet(admin, "/clients/" + clientUuid(admin, clientId) + "/client-secret"));
        return secret.path("value").asText();
    }

    private String clientUuid(final String admin, final String clientId) {
        final JsonNode clients = read(adminGet(admin, "/clients?clientId=" + encode(clientId)));
        if (clients.isEmpty()) {
            throw new IllegalStateException("No client '" + clientId + "' in realm '" + realm + "'");
        }
        return clients.get(0).path("id").asText();
    }

    private String adminToken() {
        final String form = "grant_type=password&client_id=admin-cli&username=admin&password=admin";
        final String response = post(baseUrl + "/realms/master/protocol/openid-connect/token", form, null);
        return read(response).path("access_token").asText();
    }

    private String adminUri(final String path) {
        return baseUrl + "/admin/realms/" + (path.isEmpty() ? "" : realm + path);
    }

    private void adminPost(final String admin, final String path, final JsonNode body) {
        post(adminUri(path), write(body), admin);
    }

    private String adminGet(final String admin, final String path) {
        return send(HttpRequest.newBuilder(URI.create(adminUri(path)))
                .header("Authorization", "Bearer " + admin)
                .GET());
    }

    private void adminPut(final String admin, final String path) {
        adminPut(admin, path, null);
    }

    private void adminPut(final String admin, final String path, final JsonNode body) {
        send(HttpRequest.newBuilder(URI.create(adminUri(path)))
                .header("Authorization", "Bearer " + admin)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : write(body))));
    }

    private String post(final String uri, final String body, final String bearer) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", bearer == null
                        ? "application/x-www-form-urlencoded" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return send(request);
    }

    /**
     * Sends, and fails loudly on anything that is not a 2xx.
     * <p>
     * Keycloak answers a representation it does not understand with a 400 and a precise explanation.
     * Ignoring that produces a fixture that reports success while creating nothing, and then a test that
     * fails somewhere else entirely.
     */
    private static String send(final HttpRequest.Builder request) {
        final HttpClient client = HttpClient.newHttpClient();
        try {
            final HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new IllegalStateException("Keycloak answered " + response.statusCode() + " to "
                        + request.build().method() + " " + request.build().uri() + ": " + response.body());
            }
            return response.body();
        } catch (final IOException ex) {
            throw new UncheckedIOException("Could not reach Keycloak", ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Keycloak", ex);
        }
    }

    private static JsonNode read(final String json) {
        try {
            return JSON.readTree(json);
        } catch (final com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Keycloak answered something that is not JSON: " + json, ex);
        }
    }

    private static String write(final JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (final com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not write " + node, ex);
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
