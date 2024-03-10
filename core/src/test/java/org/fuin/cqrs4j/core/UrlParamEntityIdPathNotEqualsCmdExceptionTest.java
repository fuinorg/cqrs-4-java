package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.EntityIdPath;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link UrlParamEntityIdPathNotEqualsCmdException} class.
 */
public class UrlParamEntityIdPathNotEqualsCmdExceptionTest {

    @Test
    void testCreate() {

        // PREPARE
        final EntityId entityId = Mockito.mock(EntityId.class);
        final EntityIdPath urlEntityIdPath = new EntityIdPath(entityId);
        final AggregateCommand command = Mockito.mock(AggregateCommand.class);

        final EntityId cmdEntityId = Mockito.mock(EntityId.class);
        final EntityIdPath cmdEntityIdPath = new EntityIdPath(cmdEntityId);
        when(command.getEntityIdPath()).thenReturn(cmdEntityIdPath);

        // TEST
        final UrlParamEntityIdPathNotEqualsCmdException testee = new UrlParamEntityIdPathNotEqualsCmdException(urlEntityIdPath, command);

        // VERIFY
        assertThat(testee.getUrlEntityIdPath()).isEqualTo(urlEntityIdPath);
        assertThat(testee.getCommand()).isEqualTo(command);

    }

}
