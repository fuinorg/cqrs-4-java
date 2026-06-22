package org.fuin.cqrs4j.test.helper;

import com.sun.security.auth.module.UnixSystem;
import org.fuin.objects4j.common.ThreadSafe;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Helper functions for the test modules.
 */
@ThreadSafe
public final class TestHelper {

    private TestHelper() {
        throw new UnsupportedOperationException("Cannot instantiate utility class");
    }

    /**
     * Creates a preconfigured eventstore container.
     *
     * @param version Docker image version of the eventstore image to use.
     * @return Container.
     */
    @SuppressWarnings({"java:S2095"})
    public static GenericContainer<?> createEventstoreContainer(String version) {
        return new GenericContainer<>("eventstore/eventstore:" + version)
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withUser(new UnixSystem().getUid() + ":" + new UnixSystem().getGid()))
                .withNetworkMode("bridge")
                .withExposedPorts(2113)
                .withEnv(Map.of("EVENTSTORE_MEM_DB", "TRUE",
                        "EVENTSTORE_RUN_PROJECTIONS", "All",
                        "EVENTSTORE_INSECURE", "true",
                        "EVENTSTORE_LOG", "/tmp/log-eventstore"))
                .waitingFor(new HttpWaitStrategy().withMethod("GET")
                        .forPath("/web/index.html#/")
                        .withReadTimeout(Duration.of(20, ChronoUnit.SECONDS)));
    }

    /**
     * Creates a preconfigured MariaDB container.
     *
     * @param version Docker image version of the MariaDB image to use.
     * @return Container.
     */
    @SuppressWarnings("java:S2095") // Resource will be closed after using unit test
    public static MariaDBContainer<?> createMariaDBContainer(String version) {
        return new MariaDBContainer<>("mariadb:" + version)
                .withDatabaseName("testdb")
                .withUsername("mary")
                .withPassword("abc");
    }

}
