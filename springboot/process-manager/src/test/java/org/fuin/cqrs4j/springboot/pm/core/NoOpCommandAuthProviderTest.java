package org.fuin.cqrs4j.springboot.pm.core;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link NoOpCommandAuthProvider} class.
 */
class NoOpCommandAuthProviderTest {

    @Test
    void testCreateAddsNoHeaders() {
        final NoOpCommandAuthProvider testee = new NoOpCommandAuthProvider();
        final HttpHeaders headers = HttpHeaders.of(Map.of(), (name, value) -> true);

        final HttpHeaders result = testee.create(headers);

        assertThat(result.map()).isEmpty();
    }

}
