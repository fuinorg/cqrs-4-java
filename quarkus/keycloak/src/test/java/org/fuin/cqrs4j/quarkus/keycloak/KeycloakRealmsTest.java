package org.fuin.cqrs4j.quarkus.keycloak;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link KeycloakRealms}.
 */
class KeycloakRealmsTest {

    static String base64Url(final String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static String jwt(final String payloadJson) {
        return base64Url("{\"alg\":\"none\"}") + "." + base64Url(payloadJson) + ".";
    }

    @Test
    void testRealmFromIssuer() {
        assertThat(KeycloakRealms.realmFromIssuer("http://localhost:8082/realms/master")).isEqualTo("master");
        assertThat(KeycloakRealms.realmFromIssuer("http://localhost:8082/realms/custone")).isEqualTo("custone");
    }

    @Test
    void testRealmFromIssuerWithoutSlash() {
        assertThatThrownBy(() -> KeycloakRealms.realmFromIssuer("noslash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to extract realm");
    }

    @Test
    void testBaseUri() {
        assertThat(KeycloakRealms.baseUri("http://localhost:8082/realms/master"))
                .isEqualTo("http://localhost:8082/realms/");
    }

    @Test
    void testIssuerFromBearerToken() {
        final String token = jwt("{\"iss\":\"http://localhost:8082/realms/master\",\"sub\":\"1\"}");
        assertThat(KeycloakRealms.issuerFromBearerToken(token)).contains("http://localhost:8082/realms/master");
    }

    @Test
    void testIssuerFromBearerTokenWithoutIssuer() {
        assertThat(KeycloakRealms.issuerFromBearerToken(jwt("{\"sub\":\"1\"}"))).isEmpty();
    }

    @Test
    void testIssuerFromBearerTokenInvalid() {
        assertThat(KeycloakRealms.issuerFromBearerToken(null)).isEmpty();
        assertThat(KeycloakRealms.issuerFromBearerToken("not-a-jwt")).isEmpty();
        assertThat(KeycloakRealms.issuerFromBearerToken("aaa.@@@notbase64@@@.ccc")).isEmpty();
    }

}
