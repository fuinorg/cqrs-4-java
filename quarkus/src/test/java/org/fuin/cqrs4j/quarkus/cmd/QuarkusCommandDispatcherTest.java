package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.enterprise.inject.Instance;
import jakarta.json.bind.Jsonb;
import jakarta.validation.Validator;
import org.fuin.cqrs4j.core.CommandAuthorizer;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.CommandHandlerRegistry;
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.User;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the deduplication behaviour of {@link QuarkusCommandDispatcher} when a {@link ProcessedCommandStore} is
 * supplied: a re-delivered command whose id was already recorded must not be handled a second time.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class QuarkusCommandDispatcherTest {

    private Jsonb jsonb;

    private SerializedDataTypeRegistry typeRegistry;

    private CommandAuthorizer authorizer;

    private Validator validator;

    private CommandHandlerRegistry commandHandlerRegistry;

    private Instance<CommandHandler> commandHandlers;

    private ProcessedCommandStore store;

    private CommandHandler commandHandler;

    private CommandExecutionContext executionContext;

    private MyCommand cmd;

    @BeforeEach
    public void setup() throws Exception {
        jsonb = mock(Jsonb.class);
        typeRegistry = mock(SerializedDataTypeRegistry.class);
        authorizer = mock(CommandAuthorizer.class);
        validator = mock(Validator.class);
        commandHandlerRegistry = mock(CommandHandlerRegistry.class);
        commandHandlers = mock(Instance.class);
        store = mock(ProcessedCommandStore.class);
        commandHandler = mock(CommandHandler.class);
        executionContext = mock(CommandExecutionContext.class);
        cmd = new MyCommand();

        when(executionContext.getUser()).thenReturn(mock(User.class));
        when(typeRegistry.findClass(any())).thenReturn((Class) MyCommand.class);
        when(jsonb.fromJson(any(String.class), eq(MyCommand.class))).thenReturn(cmd);
        when(validator.validate(any())).thenReturn(Collections.emptySet());
        when(commandHandlerRegistry.findHandlerClass(any())).thenReturn((Class) CommandHandler.class);
        final Instance<CommandHandler> selected = mock(Instance.class);
        when(commandHandlers.select(any(Class.class))).thenReturn(selected);
        when(selected.get()).thenReturn(commandHandler);
        when(authorizer.authorized(any(), any()))
                .thenReturn(new CommandAuthorizer.Result(true, cmd, null, List.of()));
        when(commandHandler.handle(any(), any())).thenReturn("ok");
        when(jsonb.toJson(any())).thenReturn("\"ok\"");
    }

    @Test
    public void testDuplicateCommandHandledOnce() throws Exception {

        // PREPARE: dedup store reports "new" on the first delivery, "already processed" on the re-delivery
        when(store.processed(cmd.getEventId().asString())).thenReturn(false, true);
        final QuarkusCommandDispatcher testee = new QuarkusCommandDispatcher(jsonb, typeRegistry, authorizer,
                validator, commandHandlerRegistry, commandHandlers, store);

        // TEST: same command delivered twice
        testee.dispatch("MyCommand", "{}", executionContext, List.of());
        testee.dispatch("MyCommand", "{}", executionContext, List.of());

        // VERIFY: the handler ran exactly once and the id was recorded exactly once
        verify(commandHandler, times(1)).handle(any(), any());
        verify(store, times(1)).markProcessed(cmd.getEventId().asString());

    }

    @Test
    public void testWithoutStoreHandlesEveryTime() throws Exception {

        // PREPARE: no dedup store
        final QuarkusCommandDispatcher testee = new QuarkusCommandDispatcher(jsonb, typeRegistry, authorizer,
                validator, commandHandlerRegistry, commandHandlers);

        // TEST
        testee.dispatch("MyCommand", "{}", executionContext, List.of());
        testee.dispatch("MyCommand", "{}", executionContext, List.of());

        // VERIFY: without a store every delivery is handled
        verify(commandHandler, times(2)).handle(any(), any());

    }

    /**
     * Minimal command with a stable event id.
     */
    public static class MyCommand implements Command {

        private static final EventType TYPE = new EventType("MyCommand");

        private final EventId eventId = new EventId();

        @Override
        public EventId getEventId() {
            return eventId;
        }

        @Override
        public EventType getEventType() {
            return TYPE;
        }

        @Override
        public ZonedDateTime getEventTimestamp() {
            return ZonedDateTime.parse("2020-01-01T00:00:00Z");
        }

        @Override
        @Nullable
        public EventId getCorrelationId() {
            return null;
        }

        @Override
        @Nullable
        public EventId getCausationId() {
            return null;
        }

    }

}
