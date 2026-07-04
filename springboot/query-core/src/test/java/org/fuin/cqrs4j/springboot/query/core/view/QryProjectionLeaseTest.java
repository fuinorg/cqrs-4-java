package org.fuin.cqrs4j.springboot.query.core.view;

import org.fuin.esc.api.SimpleStreamId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link QryProjectionLease} class.
 */
public class QryProjectionLeaseTest {

    @Test
    public void testConstructorAndAccessors() {

        // TEST
        final QryProjectionLease testee = new QryProjectionLease(new SimpleStreamId("MyStream"), "owner-1", 1234L);

        // VERIFY
        assertThat(testee.getStreamId()).isEqualTo("MyStream");
        assertThat(testee.getOwner()).isEqualTo("owner-1");
        assertThat(testee.getExpiresAt()).isEqualTo(1234L);

        // TEST
        testee.grantTo("owner-2", 5678L);

        // VERIFY
        assertThat(testee.getOwner()).isEqualTo("owner-2");
        assertThat(testee.getExpiresAt()).isEqualTo(5678L);

    }

}
