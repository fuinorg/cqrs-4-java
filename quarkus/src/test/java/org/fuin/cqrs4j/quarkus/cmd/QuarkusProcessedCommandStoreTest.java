package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link QuarkusProcessedCommandStore} class with a mocked {@link EntityManager} and a controllable
 * clock.
 */
public class QuarkusProcessedCommandStoreTest {

    private static final String CMD_ID = "f910c6d7-debc-46e1-ae02-9ca6f4658cf5";

    private EntityManager em;

    private long now = 1_000L;

    private QuarkusProcessedCommandStore testee;

    @BeforeEach
    public void setup() {
        em = mock(EntityManager.class);
        testee = new QuarkusProcessedCommandStore() {
            @Override
            protected long now() {
                return now;
            }
        };
        testee.em = em;
    }

    @Test
    public void testProcessedFalseWhenAbsent() {

        // PREPARE
        when(em.find(QryProcessedCommand.class, CMD_ID)).thenReturn(null);

        // TEST & VERIFY
        assertThat(testee.processed(CMD_ID)).isFalse();

    }

    @Test
    public void testProcessedTrueWhenPresent() {

        // PREPARE
        when(em.find(QryProcessedCommand.class, CMD_ID)).thenReturn(new QryProcessedCommand(CMD_ID, now));

        // TEST & VERIFY
        assertThat(testee.processed(CMD_ID)).isTrue();

    }

    @Test
    public void testMarkProcessedPersistsWhenAbsent() {

        // PREPARE
        when(em.find(QryProcessedCommand.class, CMD_ID)).thenReturn(null);

        // TEST
        testee.markProcessed(CMD_ID);

        // VERIFY
        verify(em).persist(any(QryProcessedCommand.class));

    }

    @Test
    public void testMarkProcessedNoopWhenPresent() {

        // PREPARE: already recorded
        when(em.find(QryProcessedCommand.class, CMD_ID)).thenReturn(new QryProcessedCommand(CMD_ID, now));

        // TEST
        testee.markProcessed(CMD_ID);

        // VERIFY: idempotent - no second row is written
        verify(em, never()).persist(any());

    }

}
