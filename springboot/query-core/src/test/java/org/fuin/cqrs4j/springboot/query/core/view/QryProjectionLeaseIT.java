package org.fuin.cqrs4j.springboot.query.core.view;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.fuin.utils4j.TestOmitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the distributed projection lease against a real database (MariaDB via Testcontainers).
 * The unit test ({@link QryProjectionLeaseServiceTest}) mocks the entity manager and only proves the decision
 * logic; this test proves that the real pessimistic <code>SELECT … FOR UPDATE</code> row lock actually
 * <b>serializes competing instances</b>. The load-bearing assertion is
 * {@link #concurrentAcquireGrantsExactlyOne()}: two transactions/connections race for the same lease and
 * exactly one wins.
 * <p>
 * Runs in the {@code integration-test} phase (Maven Failsafe) and requires a Docker environment.
 */
@TestOmitted("Integration test (Testcontainers) - exercises the lease service against a real database")
@SpringBootTest(classes = QryProjectionLeaseIT.TestApp.class)
@Testcontainers
class QryProjectionLeaseIT {

    private static final StreamId STREAM = new SimpleStreamId("MyView-1");

    private static final long TTL = 60_000L;

    @Container
    @SuppressWarnings("resource")
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        // MariaDB is not embedded, so schema generation must be turned on explicitly.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private QryProjectionLeaseService leaseService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void clearLeases() {
        // JPQL (entity name) so the test does not depend on the physical table naming.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                em.createQuery("DELETE FROM " + QryProjectionLease.class.getSimpleName()).executeUpdate());
    }

    @Test
    void heldLeaseBlocksOtherOwner() {
        assertThat(leaseService.acquire(STREAM, "A", TTL)).isTrue();
        assertThat(leaseService.acquire(STREAM, "B", TTL)).isFalse();
        leaseService.release(STREAM, "A");
        assertThat(leaseService.acquire(STREAM, "B", TTL)).isTrue();
    }

    @Test
    void renewKeepsOthersOut() {
        assertThat(leaseService.acquire(STREAM, "A", TTL)).isTrue();
        leaseService.renew(STREAM, "A", TTL);
        assertThat(leaseService.acquire(STREAM, "B", TTL)).isFalse();
    }

    @Test
    void concurrentAcquireGrantsExactlyOne() throws Exception {

        // PREPARE: seed an already-expired lease row so the two concurrent acquires contend on an EXISTING
        // row (SELECT ... FOR UPDATE), avoiding the one-time first-insert primary-key race.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                em.persist(new QryProjectionLease(STREAM, "seed", 0L)));

        final CyclicBarrier barrier = new CyclicBarrier(2);
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Future<Boolean> resultA = pool.submit(() -> {
                barrier.await();
                return leaseService.acquire(STREAM, "A", TTL);
            });
            final Future<Boolean> resultB = pool.submit(() -> {
                barrier.await();
                return leaseService.acquire(STREAM, "B", TTL);
            });

            final boolean gotA = resultA.get();
            final boolean gotB = resultB.get();

            // VERIFY: exactly one instance won the lease...
            assertThat(gotA ^ gotB).as("exactly one owner acquires the lease").isTrue();

            // ...and the stored owner is the winner.
            final String winner = gotA ? "A" : "B";
            final QryProjectionLease lease = new TransactionTemplate(transactionManager).execute(status ->
                    em.find(QryProjectionLease.class, STREAM.asString()));
            assertThat(lease).isNotNull();
            assertThat(lease.getOwner()).isEqualTo(winner);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Minimal test application: enables auto-configuration (DataSource / JPA), scans the lease entity and
     * provides the lease service. No event-store beans are needed.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.fuin.cqrs4j.springboot.query.core.view")
    static class TestApp {

        @Bean
        QryProjectionLeaseService leaseService() {
            return new QryProjectionLeaseService();
        }

    }

}
