package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.ddd4j.core.SimpleRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CommandRestController}.
 */
class CommandRestControllerTest {

    @Test
    void testDispatchesWithCallerFromProvider() throws Exception {

        // PREPARE
        final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
        final CommandExecutionContextProvider provider = mock(CommandExecutionContextProvider.class);
        final CommandExecutionContext context = mock(CommandExecutionContext.class);
        final List<SimpleRole> roles = List.of(new SimpleRole("admin"));
        when(provider.current()).thenReturn(context);
        when(provider.currentUserRoles()).thenReturn(roles);
        when(dispatcher.dispatch(anyString(), isNull(), anyString(), any(), anyList())).thenReturn("OK");

        // TEST
        final String result = new CommandRestController(dispatcher, provider).cmd("MyCommand", "{}");

        // VERIFY - the caller comes from the provider, not from a method parameter, which is what
        // allows the controller to implement the client-usable interface.
        assertThat(result).isEqualTo("OK");
        verify(dispatcher).dispatch(eq("MyCommand"), isNull(), eq("{}"), eq(context), eq(roles));
    }

    @Test
    void testWrapsFailureAsUnchecked() throws Exception {

        // PREPARE
        final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
        final CommandExecutionContextProvider provider = mock(CommandExecutionContextProvider.class);
        when(provider.current()).thenReturn(mock(CommandExecutionContext.class));
        when(provider.currentUserRoles()).thenReturn(List.of());
        final CommandExecutionFailedException cause =
                new CommandExecutionFailedException(new IllegalStateException("Boom"));
        when(dispatcher.dispatch(anyString(), isNull(), anyString(), any(), anyList())).thenThrow(cause);

        // TEST & VERIFY - the interface declares no checked exception, so it must be wrapped.
        assertThatThrownBy(() -> new CommandRestController(dispatcher, provider).cmd("MyCommand", "{}"))
                .isInstanceOf(CommandExecutionRuntimeException.class)
                .hasCause(cause);
    }

}
