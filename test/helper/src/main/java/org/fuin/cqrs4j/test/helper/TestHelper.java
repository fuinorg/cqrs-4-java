package org.fuin.cqrs4j.test.helper;

import com.sun.security.auth.module.UnixSystem;
import org.fuin.cqrs4j.test.model.PersonCreatedEvent;
import org.fuin.cqrs4j.test.model.PersonId;
import org.fuin.cqrs4j.test.model.PersonName;
import org.fuin.ddd4j.core.AggregateVersion;
import org.fuin.esc.api.CommonEvent;
import org.fuin.esc.api.SimpleCommonEvent;
import org.fuin.esc.api.TypeName;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Helper functions for the test modules.
 */
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
    public static MariaDBContainer<?> createMariaDBContainer(String version) {
        return new MariaDBContainer<>("mariadb:" + version)
                .withDatabaseName("testdb")
                .withUsername("mary")
                .withPassword("abc");
    }

    /**
     * Creates a {@link PersonCreatedEvent} packed into a {@link CommonEvent}.
     *
     * @param id   Unique person identifier.
     * @param name Name of the person.
     * @return Event to store.
     */
    public static CommonEvent createPersonCreatedEvent(PersonId id, PersonName name) {
        final org.fuin.esc.api.EventId eventId = new org.fuin.esc.api.EventId();
        final PersonCreatedEvent event = PersonCreatedEvent.builder()
                .aggregateVersion(AggregateVersion.valueOf(0))
                .entityIdPath(id)
                .eventId(new org.fuin.ddd4j.core.EventId(eventId.asBaseType()))
                .id(id)
                .name(name)
                .timestamp(ZonedDateTime.now())
                .build();
        return new SimpleCommonEvent(
                eventId,
                new TypeName(PersonCreatedEvent.TYPE.asBaseType()),
                event);
    }

}
