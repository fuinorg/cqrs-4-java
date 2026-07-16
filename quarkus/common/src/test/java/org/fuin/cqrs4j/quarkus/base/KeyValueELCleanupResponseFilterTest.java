package org.fuin.cqrs4j.quarkus.base;

import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for the {@link KeyValueELCleanupResponseFilter} class.
 */
class KeyValueELCleanupResponseFilterTest {

    @AfterEach
    void tearDown() {
        KeyValueEL.clear();
    }

    @Test
    void testFilterClearsThreadLocalProcessor() {
        final KeyValueELCleanupResponseFilter testee = new KeyValueELCleanupResponseFilter();

        // Define a bean on this thread, then verify it survives on the same thread (the leak the filter prevents).
        assertThat(KeyValueEL.replace("${one}", new KeyValue("one", "1"))).isEqualTo("1");
        assertThat(KeyValueEL.replace("${one}")).isEqualTo("1");

        // The response filter must clear the per-thread processor (request/response contexts are unused).
        testee.filter(null, null);

        assertThatThrownBy(() -> KeyValueEL.replace("${one}")).isInstanceOf(Exception.class);
    }

}
