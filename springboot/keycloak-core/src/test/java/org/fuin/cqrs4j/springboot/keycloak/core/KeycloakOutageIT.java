package org.fuin.cqrs4j.springboot.keycloak.core;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fault-injection test for tenant discovery (K1) against a <b>real Keycloak</b>, covering the three things
 * the unit tests cannot show because they stub the discovery away:
 *
 * <ul>
 *     <li>a failed discovery is not repeated on the next call - a Keycloak that is down is contacted once
 *         per backoff window, not once per request,</li>
 *     <li>discovery is attempted again once the backoff elapsed, so an issuer is not refused forever,</li>
 *     <li>an issuer that was resolved <b>keeps working while Keycloak is down</b> - the negative cache
 *         covers discovery only, and a tenant already in the cache never goes back to the network.</li>
 * </ul>
 *
 * The container is bound to a <b>fixed host port</b> so the issuer URI survives a stop and start. Testcontainers
 * hands out a different mapped port on every start, and the issuer URI is the cache key as well as the address,
 * so a moving port would make the "same issuer, Keycloak went away and came back" sequence untestable.
 * <p>
 * Only the master realm is used, which every Keycloak has, so no realm import is needed. Requires a Docker
 * environment.
 */
class KeycloakOutageIT {

    /** Fixed so the issuer URI stays the same across a container restart; see the class comment. */
    private static final int KEYCLOAK_PORT = 18082;

    private static final String ISSUER = "http://localhost:" + KEYCLOAK_PORT + "/realms/master";

    private static KeycloakContainer keycloak;

    @AfterAll
    static void stopKeycloak() {
        if (keycloak != null && keycloak.isRunning()) {
            keycloak.stop();
        }
    }

    @Test
    void discoveryIsNotRepeatedWhileDownIsRetriedAfterTheBackoffAndSurvivesALaterOutage()
            throws InterruptedException {

        // PREPARE: Keycloak is not running yet - the very first discovery has nothing to talk to
        final CountingRepository testee = new CountingRepository();

        // TEST & VERIFY: the failure is remembered, so the second call does not go to the network again.
        // Without this a down identity provider is contacted by every single request carrying the issuer.
        assertThatThrownBy(() -> testee.findByIssuer(ISSUER)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> testee.findByIssuer(ISSUER)).isInstanceOf(RuntimeException.class);
        assertThat(testee.discoveries()).isEqualTo(1);

        // TEST: Keycloak appears
        startKeycloak();

        // VERIFY: once the backoff elapsed the issuer is tried again rather than being refused forever
        Thread.sleep(KeycloakTenantRepository.INITIAL_RETRY_DELAY_MILLIS + 500);
        assertThat(testee.findByIssuer(ISSUER)).isPresent();
        assertThat(testee.discoveries()).isEqualTo(2);

        // TEST: Keycloak goes away again, after the tenant was resolved
        keycloak.stop();

        // VERIFY: the resolved tenant keeps being served without touching the network. This is the
        // "last known good" half - an outage must not invalidate issuers that were already working, only
        // block ones that were never seen.
        assertThat(testee.findByIssuer(ISSUER)).isPresent();
        assertThat(testee.findByIssuer(ISSUER)).isPresent();
        assertThat(testee.discoveries()).isEqualTo(2);
    }

    private static void startKeycloak() {
        keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0");
        keycloak.setPortBindings(List.of(KEYCLOAK_PORT + ":8080"));
        keycloak.start();
    }

    /**
     * Repository that counts how often it actually went to Keycloak, while still performing the real
     * discovery. Counting is what distinguishes "answered from the cache" from "asked again".
     */
    private static final class CountingRepository extends KeycloakTenantRepository {

        private final AtomicInteger discoveries = new AtomicInteger();

        private CountingRepository() {
            super(ISSUER, event -> {
                // Nothing to do - tenant events are not part of this test
            });
        }

        @Override
        protected JwtTenant createTenant(final String issuerUri) {
            discoveries.incrementAndGet();
            return super.createTenant(issuerUri);
        }

        int discoveries() {
            return discoveries.get();
        }

    }

}
