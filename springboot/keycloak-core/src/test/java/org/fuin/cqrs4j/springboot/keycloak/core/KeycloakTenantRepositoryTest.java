package org.fuin.cqrs4j.springboot.keycloak.core;

import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
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

    @Test
    void testATenantIsKeptWhileItsTimeToLiveLasts() {

        // PREPARE
        final TestRepository testee = new TestRepository();
        testee.findByIssuer(OTHER);
        testee.realmGone(true);

        // TEST: not yet due
        testee.advance(KeycloakTenantRepository.DEFAULT_TTL_MILLIS - 1);
        testee.revalidate();

        // VERIFY: nothing was probed and the tenant is still there
        assertThat(testee.probes()).isZero();
        assertThat(testee.findByIssuer(OTHER)).isPresent();

    }

    @Test
    void testAVanishedRealmIsRemovedAndAnnounced() {

        // PREPARE
        final TestRepository testee = new TestRepository();
        testee.findByIssuer(OTHER);
        testee.realmGone(true);

        // TEST: the time to live has passed and the realm answers "no such realm"
        testee.advance(KeycloakTenantRepository.DEFAULT_TTL_MILLIS);
        testee.revalidate();

        // VERIFY: gone from the repository and announced, so the caches downstream can drop it
        assertThat(testee.getTenantIds()).isEmpty();
        assertThat(testee.removedTenants()).containsExactly("other");

    }

    /**
     * The fail-safe: a Keycloak that cannot answer says nothing about whether the realm exists. Treating
     * that as a removal would log out every tenant on any hiccup.
     */
    @Test
    void testAnUnreachableRealmIsKept() {

        // PREPARE
        final TestRepository testee = new TestRepository();
        testee.findByIssuer(OTHER);
        testee.probeFailure(new IllegalStateException("connection refused"));

        // TEST
        testee.advance(KeycloakTenantRepository.DEFAULT_TTL_MILLIS);
        testee.revalidate();

        // VERIFY: still known, and nothing was announced
        assertThat(testee.findByIssuer(OTHER)).isPresent();
        assertThat(testee.removedTenants()).isEmpty();

    }

    @Test
    void testAStillExistingRealmIsRevalidatedRatherThanProbedEveryTime() {

        // PREPARE
        final TestRepository testee = new TestRepository();
        testee.findByIssuer(OTHER);

        // TEST: first sweep after the time to live confirms the realm, the next one is too early again
        testee.advance(KeycloakTenantRepository.DEFAULT_TTL_MILLIS);
        testee.revalidate();
        testee.revalidate();

        // VERIFY: the confirmation extended the time to live
        assertThat(testee.probes()).isEqualTo(1);
        assertThat(testee.findByIssuer(OTHER)).isPresent();

    }

    /**
     * Repository with a controllable clock and a discovery that can be made to fail, so the negative cache
     * can be observed without a Keycloak.
     */
    private static final class TestRepository extends KeycloakTenantRepository {

        private final AtomicInteger discoveries = new AtomicInteger();

        private final AtomicInteger probes = new AtomicInteger();

        private final List<String> removed;

        private long now = 1_000_000L;

        private RuntimeException failure;

        private boolean realmGone;

        private RuntimeException probeFailure;

        private TestRepository() {
            this(new ArrayList<>());
        }

        private TestRepository(final List<String> removed) {
            super(MASTER, event -> {
                if (event instanceof TenantRemovedEvent removedEvent) {
                    removed.add(removedEvent.tenant().getTenantId().name());
                }
            });
            this.removed = removed;
        }

        void realmGone(final boolean gone) {
            this.realmGone = gone;
        }

        void probeFailure(final RuntimeException ex) {
            this.probeFailure = ex;
        }

        int probes() {
            return probes.get();
        }

        List<String> removedTenants() {
            return removed;
        }

        @Override
        protected boolean realmExists(final String issuerUri) {
            probes.incrementAndGet();
            if (probeFailure != null) {
                throw probeFailure;
            }
            return !realmGone;
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
