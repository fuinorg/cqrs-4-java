package org.fuin.cqrs4j.quarkus.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.fuin.cqrs4j.quarkus.base.EventstoreConfig;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;

import static org.fuin.cqrs4j.test.helper.TestHelper.createEventstoreContainer;

public class EventstoreResource implements QuarkusTestResourceLifecycleManager {

    static GenericContainer es = createEventstoreContainer("24.10");

    @Override
    public Map<String, String> start() {
        es.start();
        return Map.of(EventstoreConfig.KEY_PORT, "" + es.getFirstMappedPort());
    }

    @Override
    public void stop() {
        es.stop();
    }

}
