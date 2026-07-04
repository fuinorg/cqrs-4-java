package org.fuin.cqrs4j.springboot.test.pm;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.fuin.cqrs4j.core.ProcessTimeoutHandler;
import org.fuin.cqrs4j.springboot.pm.core.ProcessManagerTimeout;
import org.fuin.cqrs4j.springboot.pm.core.ProcessManagerTimeoutDeadLetter;
import org.fuin.cqrs4j.springboot.pm.core.ProcessTimeoutConfig;
import org.fuin.cqrs4j.springboot.pm.core.ProcessTimeoutRepository;
import org.fuin.cqrs4j.springboot.pm.core.ProcessTimeoutSweeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free end-to-end test for the process-timeout registry + sweeper against an embedded HSQLDB. It exercises
 * the real {@link ProcessTimeoutRepository} JPQL and the {@link ProcessTimeoutSweeper} dispatch: an armed timeout
 * whose deadline has passed is handed to the handler and removed; a cancelled timeout never fires; and a handler
 * that keeps failing lands the timeout in the dead-letter table.
 */
@SpringBootTest(classes = ProcessTimeoutSweeperSliceTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:hsqldb:mem:pm-timeout-test",
                "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class ProcessTimeoutSweeperSliceTest {

    private static final String PROCESS_ID = "p-1";

    private static final String PROCESS_TYPE = "OrderProcess";

    @Autowired
    private ProcessTimeoutRepository repository;

    @Autowired
    private ProcessTimeoutSweeper sweeper;

    @Autowired
    private RecordingTimeoutHandler handler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    private TransactionTemplate txt;

    @BeforeEach
    void setUp() {
        txt = new TransactionTemplate(transactionManager);
        txt.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM " + ProcessManagerTimeout.class.getSimpleName()).executeUpdate();
            em.createQuery("DELETE FROM " + ProcessManagerTimeoutDeadLetter.class.getSimpleName()).executeUpdate();
        });
        handler.reset();
    }

    private void arm(final long deadlineTs) {
        txt.executeWithoutResult(status -> repository.arm(PROCESS_ID, PROCESS_TYPE, 1, deadlineTs, "await-ack"));
    }

    private long timeoutCount() {
        return txt.execute(status ->
                em.createQuery("SELECT COUNT(t) FROM ProcessManagerTimeout t", Long.class).getSingleResult());
    }

    private long deadLetterCount() {
        return txt.execute(status ->
                em.createQuery("SELECT COUNT(d) FROM ProcessManagerTimeoutDeadLetter d", Long.class).getSingleResult());
    }

    @Test
    void dueTimeoutIsHandledAndRemoved() {
        // PREPARE: arm a timeout whose deadline is already in the past
        arm(1L);

        // TEST
        sweeper.drain();

        // VERIFY: handler fired once for our process and the pending row is gone
        assertThat(handler.count()).isEqualTo(1);
        assertThat(handler.lastProcessId()).isEqualTo(PROCESS_ID);
        assertThat(timeoutCount()).isZero();
    }

    @Test
    void cancelledTimeoutDoesNotFire() {
        // PREPARE
        arm(1L);
        txt.executeWithoutResult(status -> repository.cancel(PROCESS_ID));

        // TEST
        sweeper.drain();

        // VERIFY
        assertThat(handler.count()).isZero();
        assertThat(timeoutCount()).isZero();
    }

    @Test
    void notYetDueTimeoutIsLeftAlone() {
        // PREPARE: deadline far in the future
        arm(System.currentTimeMillis() + 3_600_000L);

        // TEST
        sweeper.drain();

        // VERIFY: not handled, still pending
        assertThat(handler.count()).isZero();
        assertThat(timeoutCount()).isEqualTo(1);
    }

    @Test
    void failingHandlerEventuallyDeadLetters() {
        // PREPARE: handler always fails; the test app caps retries at 2
        handler.setFail(true);
        arm(1L);

        // TEST: each sweep re-reads the still-due row and records a failure
        sweeper.drain();
        assertThat(timeoutCount()).isEqualTo(1);
        assertThat(deadLetterCount()).isZero();
        sweeper.drain();

        // VERIFY: retries exhausted -> moved to dead-letter
        assertThat(timeoutCount()).isZero();
        assertThat(deadLetterCount()).isEqualTo(1);
    }

    /**
     * Minimal slice wiring the real repository + sweeper + a recording handler on embedded HSQLDB.
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @EntityScan("org.fuin.cqrs4j.springboot.pm.core")
    static class TestApp {

        @Bean
        ProcessTimeoutRepository processTimeoutRepository() {
            return new ProcessTimeoutRepository();
        }

        @Bean
        RecordingTimeoutHandler recordingTimeoutHandler() {
            return new RecordingTimeoutHandler();
        }

        @Bean
        ProcessTimeoutSweeper processTimeoutSweeper(final ProcessTimeoutRepository repository,
                                                    final ObjectProvider<ProcessTimeoutHandler> handlers,
                                                    final PlatformTransactionManager transactionManager) {
            return new ProcessTimeoutSweeper(repository, new ProcessTimeoutConfig("*/5 * * * * *", 100, 2),
                    handlers, transactionManager);
        }

    }

    /**
     * Test timeout handler that counts invocations and can be toggled to fail.
     */
    static class RecordingTimeoutHandler implements ProcessTimeoutHandler {

        private final AtomicInteger count = new AtomicInteger();

        private volatile boolean fail;

        private volatile String lastProcessId = "";

        @Override
        public void onTimeout(final DueProcessTimeout timeout) {
            count.incrementAndGet();
            lastProcessId = timeout.processId();
            if (fail) {
                throw new IllegalStateException("Simulated timeout-handling failure");
            }
        }

        void reset() {
            count.set(0);
            fail = false;
            lastProcessId = "";
        }

        void setFail(final boolean fail) {
            this.fail = fail;
        }

        int count() {
            return count.get();
        }

        String lastProcessId() {
            return lastProcessId;
        }

    }

}
