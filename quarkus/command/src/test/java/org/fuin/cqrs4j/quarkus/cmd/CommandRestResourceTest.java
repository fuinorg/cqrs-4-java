package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.HttpHeaders;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.ddd4j.core.SimpleRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CommandRestResource}.
 */
class CommandRestResourceTest {

    @Test
    void testDispatchesWithContentTypeFromHeaders() throws Exception {

        // PREPARE
        final QuarkusCommandDispatcher dispatcher = mock(QuarkusCommandDispatcher.class);
        final CommandRestResource testee = testee(dispatcher, "application/json; version=2");
        when(dispatcher.dispatch(anyString(), anyString(), anyString(), any(), anyList())).thenReturn("OK");

        // TEST
        final String result = testee.command("MyCommand", "{}");

        // VERIFY - the Content-Type is passed through unchanged so the dispatcher can up-cast by the
        // exact media type, not just "some JSON".
        assertThat(result).isEqualTo("OK");
        final ArgumentCaptor<String> contentType = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(dispatcher)
                .dispatch(eq("MyCommand"), contentType.capture(), eq("{}"), any(), anyList());
        assertThat(contentType.getValue()).isEqualTo("application/json; version=2");
    }

    @Test
    void testWrapsFailureAsUnchecked() throws Exception {

        // PREPARE
        final QuarkusCommandDispatcher dispatcher = mock(QuarkusCommandDispatcher.class);
        final CommandRestResource testee = testee(dispatcher, "application/json");
        final CommandExecutionFailedException cause =
                new CommandExecutionFailedException(new IllegalStateException("Boom"));
        when(dispatcher.dispatch(anyString(), anyString(), anyString(), any(), anyList())).thenThrow(cause);

        // TEST & VERIFY - the interface declares no checked exception, so it must be wrapped.
        assertThatThrownBy(() -> testee.command("MyCommand", "{}"))
                .isInstanceOf(CommandExecutionRuntimeException.class)
                .hasCause(cause);
    }

    @SuppressWarnings("unchecked")
    private Instance<CommandUserRolesProvider> unsatisfied() {
        final Instance<CommandUserRolesProvider> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(true);
        return instance;
    }

    @Test
    void testUsesRolesFromProviderWhenPresent() throws Exception {

        // PREPARE
        final QuarkusCommandDispatcher dispatcher = mock(QuarkusCommandDispatcher.class);
        final CommandRestResource testee = testee(dispatcher, "application/json");
        final CommandUserRolesProvider provider = mock(CommandUserRolesProvider.class);
        when(provider.currentUserRoles()).thenReturn(List.of(new SimpleRole("admin")));
        @SuppressWarnings("unchecked")
        final Instance<CommandUserRolesProvider> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.get()).thenReturn(provider);
        testee.userRolesProvider = instance;
        when(dispatcher.dispatch(anyString(), anyString(), anyString(), any(), anyList())).thenReturn("OK");

        // TEST
        testee.command("MyCommand", "{}");

        // VERIFY - an application that supplies a provider has its roles handed to the dispatcher.
        org.mockito.Mockito.verify(dispatcher)
                .dispatch(eq("MyCommand"), anyString(), eq("{}"), any(), eq(List.of(new SimpleRole("admin"))));
    }

    private CommandRestResource testee(final QuarkusCommandDispatcher dispatcher, final String contentType) {
        final HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getHeaderString(HttpHeaders.CONTENT_TYPE)).thenReturn(contentType);
        final CommandRestResource testee = new CommandRestResource();
        testee.headers = headers;
        testee.dispatcher = dispatcher;
        testee.executionContext = mock(CommandExecutionContext.class);
        testee.userRolesProvider = unsatisfied();
        return testee;
    }

}
