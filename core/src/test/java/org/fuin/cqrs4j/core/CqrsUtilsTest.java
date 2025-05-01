package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link CqrsUtils} class.
 */
class CqrsUtilsTest {

    @Test
    void testCalculateAdler32Checksum() {

        assertThat(CqrsUtils.calculateAdler32Checksum(List.of(new EventType("A"))))
                .isEqualTo(4325442L);

        assertThat(CqrsUtils.calculateAdler32Checksum(List.of(new EventType("A"), new EventType("B"))))
                .isEqualTo(12976260L);

    }

}