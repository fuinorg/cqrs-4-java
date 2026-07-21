package org.fuin.cqrs4j.quarkus.base;

import jakarta.validation.Validator;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;
import org.fuin.objects4j.core.Validators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link ThreadLocalCleanupResponseFilter} class.
 */
class ThreadLocalCleanupResponseFilterTest {

    @AfterEach
    void tearDown() {
        KeyValueEL.clear();
        Validators.clear();
    }

    @Test
    void testFilterClearsThreadLocalValidator() {
        final ThreadLocalCleanupResponseFilter testee = new ThreadLocalCleanupResponseFilter();
        final Validator before = Validators.get();

        // The response filter must clear the per-thread validator (request/response contexts are unused).
        testee.filter(null, null);

        assertThat(Validators.get()).isNotSameAs(before);
    }

    @Test
    void testFilterClearsThreadLocalProcessor() {
        final ThreadLocalCleanupResponseFilter testee = new ThreadLocalCleanupResponseFilter();

        // Define a bean on this thread, then verify it survives on the same thread (the leak the filter
        // prevents): the second call only defines "two", but "one" still resolves.
        assertThat(KeyValueEL.replace("${one}", new KeyValue("one", "1"))).isEqualTo("1");
        assertThat(KeyValueEL.replace("${one} ${two}", new KeyValue("two", "2"))).isEqualTo("1 2");

        // The response filter must clear the per-thread processor (request/response contexts are unused).
        testee.filter(null, null);

        // "one" is gone now, so the message cannot be rendered any more and is returned unreplaced.
        assertThat(KeyValueEL.replace("${one} ${two}", new KeyValue("two", "2"))).isEqualTo("${one} ${two}");
    }

}
