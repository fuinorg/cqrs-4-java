package org.fuin.cqrs4j.jpa.query;

import org.fuin.esc.api.SimpleStreamId;
import org.fuin.esc.api.StreamId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link QryProjectionPosition} class.
 */
class QryProjectionPositionTest {

    @Test
    void testCreateAndSetGet() {

        final StreamId streamId = new SimpleStreamId("streamId");
        long nextPos = 4711;
        final QryProjectionPosition testee = new QryProjectionPosition(streamId, nextPos);
        assertThat(testee.getStreamId()).isEqualTo(streamId);
        assertThat(testee.getNextPos()).isEqualTo(nextPos);

        nextPos = nextPos + 1;
        testee.setNextPosition(nextPos);
        assertThat(testee.getNextPos()).isEqualTo(nextPos);

    }

}
