package org.fuin.cqrs4j.quarkus.cmd;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link QryProcessedCommand} class.
 */
public class QryProcessedCommandTest {

    @Test
    public void testConstructorAndAccessors() {

        // PREPARE & TEST
        final QryProcessedCommand testee = new QryProcessedCommand("cmd-1", 1_234L);

        // VERIFY
        assertThat(testee.getCommandId()).isEqualTo("cmd-1");
        assertThat(testee.getProcessedTs()).isEqualTo(1_234L);
        assertThat(testee.toString()).contains("cmd-1").contains("1234");

    }

}
