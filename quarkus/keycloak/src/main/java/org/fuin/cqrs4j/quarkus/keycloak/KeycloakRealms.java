package org.fuin.cqrs4j.quarkus.keycloak;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.fuin.objects4j.common.ThreadSafe;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility methods for deriving Keycloak realm / tenant information from OIDC issuer URIs and raw JWT bearer
 * tokens.
 */
@ThreadSafe
public final class KeycloakRealms {

    private KeycloakRealms() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of a utility class");
    }

    /**
     * Extracts the realm name from an issuer URI (the part after the last slash).
     *
     * @param issuerUri Issuer URI like "http://localhost:8082/realms/master".
     * @return Realm name (e.g. "master").
     */
    public static String realmFromIssuer(final String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuerUri==null");
        final int p = issuerUri.lastIndexOf('/');
        if (p < 0) {
            throw new IllegalArgumentException("Failed to extract realm from issuer: " + issuerUri);
        }
        return issuerUri.substring(p + 1);
    }

    /**
     * Derives the base URI (everything up to and including the last slash) from the master realm issuer URI.
     *
     * @param masterIssuerUri Master realm issuer URI like "http://localhost:8082/realms/master".
     * @return Base URI like "http://localhost:8082/realms/".
     */
    public static String baseUri(final String masterIssuerUri) {
        Objects.requireNonNull(masterIssuerUri, "masterIssuerUri==null");
        final int p = masterIssuerUri.lastIndexOf('/');
        if (p < 0) {
            throw new IllegalArgumentException("Cannot find realm in: '" + masterIssuerUri + "'");
        }
        return masterIssuerUri.substring(0, p + 1);
    }

    /**
     * Reads the "iss" (issuer) claim from a raw JWT bearer token <b>without verifying its signature</b>. Only the
     * (unverified) payload is inspected to select the tenant; the actual signature and issuer verification is
     * performed afterwards by quarkus-oidc using the resolved tenant configuration.
     *
     * @param bearerToken Raw JWT (three base64url parts separated by dots).
     * @return Issuer claim if present and parseable, otherwise empty.
     */
    public static Optional<String> issuerFromBearerToken(final String bearerToken) {
        if (bearerToken == null) {
            return Optional.empty();
        }
        final String[] parts = bearerToken.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            final byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            try (var reader = Json.createReader(new ByteArrayInputStream(json))) {
                final JsonObject payload = reader.readObject();
                return Optional.ofNullable(payload.getString("iss", null));
            }
        } catch (final RuntimeException ex) {
            return Optional.empty();
        }
    }

}
