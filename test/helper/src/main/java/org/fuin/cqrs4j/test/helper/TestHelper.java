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
        return new GenericContainer<>("kurrentplatform/kurrentdb:" + version)
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withUser(new UnixSystem().getUid() + ":" + new UnixSystem().getGid()))
                .withNetworkMode("bridge")
                .withExposedPorts(2113)
                .withEnv(Map.of("KURRENTDB_MEM_DB", "TRUE",
                        "KURRENTDB_RUN_PROJECTIONS", "All",
                        "KURRENTDB_INSECURE", "true",
                        "KURRENTDB_LOG", "/tmp/log-kurrentdb"))
                .waitingFor(new HttpWaitStrategy().withMethod("GET")
                        .forPath("/web/index.html#/")
                        .withReadTimeout(Duration.of(20, ChronoUnit.SECONDS)));
    }

    /**
     * Creates a preconfigured Keycloak container.
     * <p>
     * Started with {@code start-dev}, which derives the issuer from the request it receives - so the
     * randomly mapped port needs no further configuration and no fixed host-port binding. Pair it with
     * {@link KeycloakRealm} to get a realm and tokens.
     *
     * @param version Docker image version of the Keycloak image to use, e.g. {@code 26.0.7}. Pinned by
     *                the caller on purpose: the loopback-redirect relaxation and the client-assertion
     *                handling a test may depend on are behaviour of a specific server version.
     *
     * @return Container.
     */
    @SuppressWarnings({"java:S2095"}) // Resource will be closed after using unit test
    public static GenericContainer<?> createKeycloakContainer(String version) {
        return new GenericContainer<>("quay.io/keycloak/keycloak:" + version)
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withCommand("start-dev")
                .withExposedPorts(8080)
                .waitingFor(new HttpWaitStrategy().withMethod("GET")
                        .forPath("/realms/master")
                        .forPort(8080)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.of(3, ChronoUnit.MINUTES)));
    }

    /**
     * Returns the base URL of a container started by {@link #createKeycloakContainer(String)}.
     *
     * @param keycloak Running container.
     *
     * @return Base URL without a trailing slash.
     */
    public static String keycloakUrl(final GenericContainer<?> keycloak) {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
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
