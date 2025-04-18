package org.fuin.cqrs4j.quarkus.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MariaDBContainer;

import java.util.Collections;
import java.util.Map;

import static org.fuin.cqrs4j.test.helper.TestHelper.createMariaDBContainer;

public class MariaDbResource implements QuarkusTestResourceLifecycleManager {

	static MariaDBContainer<?> db = createMariaDBContainer("11");;

	@Override
	public Map<String, String> start() {
		db.start();
		return Map.of("quarkus.datasource.jdbc.url", db.getJdbcUrl());
	}

	@Override
	public void stop() {
		db.stop();
	}
	
}
