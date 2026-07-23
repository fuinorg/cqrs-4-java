package org.fuin.cqrs4j.springboot.keycloak.core;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for the {@link KeycloakTenantRepository} class, in particular the negative cache that keeps a down
 * Keycloak from being contacted again by every single request.
 */
class KeycloakTenantRepositoryTest {

    private static final String MASTER = "http://localhost:8082/realms/master";

    private static final String OTHER = "http://localhost:8082/realms/other";

    @Test
    void testAFailedDiscoveryIsNotRepeatedImmediately() {

        // PREPARE: Keycloak is down
        final TestRepository testee = new TestRepository();
        testee.failWith(new IllegalArgumentException("Unable to resolve the Configuration"));

        // TEST: the first request pays for the failed call
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);

        // VERIFY: the next ones fail immediately with the same answer instead of calling again - this is
        // what stops a down identity provider from occupying every request thread
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(testee.discoveries()).isEqualTo(1);
    }

    @Test
    void testDiscoveryIsAttemptedAgainAfterTheDelay() {

        // PREPARE
        final TestRepository testee = new TestRepository();
        testee.failWith(new IllegalArgumentException("down"));
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);

        // TEST: the retry window elapses
        testee.advance(KeycloakTenantRepository.INITIAL_RETRY_DELAY_MILLIS);

        // VERIFY: it tries again rather than refusing the issuer forever
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(testee.discoveries()).isEqualTo(2);
    }

    @Test
    void testTheDelayGrowsWithConsecutiveFailures() {

        // PREPARE
        final TestRepository testee = new TestRepository();
        testee.failWith(new IllegalArgumentException("down"));
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);
        testee.advance(KeycloakTenantRepository.INITIAL_RETRY_DELAY_MILLIS);
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(testee.discoveries()).isEqualTo(2);

        // TEST: waiting the *initial* delay again is no longer enough after the second failure
        testee.advance(KeycloakTenantRepository.INITIAL_RETRY_DELAY_MILLIS);
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(testee.discoveries()).isEqualTo(2);

        // VERIFY: the doubled delay does let it through
        testee.advance(KeycloakTenantRepository.INITIAL_RETRY_DELAY_MILLIS);
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(testee.discoveries()).isEqualTo(3);
    }

    @Test
    void testASuccessfulDiscoveryClearsTheFailure() {

        // PREPARE
        final TestRepository testee = new TestRepository();
        testee.failWith(new IllegalArgumentException("down"));
        assertThatThrownBy(() -> testee.findByIssuer(OTHER)).isInstanceOf(IllegalArgumentException.class);

        // TEST: Keycloak comes back
        testee.failWith(null);
        testee.advance(KeycloakTenantRepository.INITIAL_RETRY_DELAY_MILLIS);
        assertThat(testee.findByIssuer(OTHER)).isPresent();

        // VERIFY: the tenant is cached, so no further discovery happens at all
        assertThat(testee.findByIssuer(OTHER)).isPresent();
        assertThat(testee.discoveries()).isEqualTo(2);
    }

    @Test
    void testAnIssuerOutsideTheMasterRealmIsRejectedWithoutAnyCall() {
        final TestRepository testee = new TestRepository();

        assertThatThrownBy(() -> testee.findByIssuer("http://evil.example.com/realms/other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not start with");
        assertThat(testee.discoveries()).isZero();
    }

    /**
     * Repository with a controllable clock and a discovery that can be made to fail, so the negative cache
     * can be observed without a Keycloak.
     */
    private static final class TestRepository extends KeycloakTenantRepository {

        private final AtomicInteger discoveries = new AtomicInteger();

        private long now = 1_000_000L;

        private RuntimeException failure;

        private TestRepository() {
            super(MASTER, event -> {
                // Nothing to do - the events are not part of these tests
            });
        }

        void failWith(final RuntimeException failure) {
            this.failure = failure;
        }

        void advance(final long millis) {
            now = now + millis;
        }

        int discoveries() {
            return discoveries.get();
        }

        @Override
        protected JwtTenant createTenant(final String issuerUri) {
            discoveries.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            // The public constructor performs OIDC discovery; this one takes the result directly.
            return new JwtTenant(issuerUri, Map.of("issuer", issuerUri, "jwks_uri", issuerUri + "/certs"));
        }

        @Override
        protected long now() {
            return now;
        }

    }

}
