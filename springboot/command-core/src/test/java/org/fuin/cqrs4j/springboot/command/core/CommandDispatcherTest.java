package org.fuin.cqrs4j.springboot.command.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.validation.Validator;
import org.fuin.cqrs4j.core.CommandAuthorizer;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.CommandHandlerRegistry;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.fuin.cqrs4j.jackson.AbstractCommand;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.User;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the deduplication behaviour of {@link CommandDispatcher} when a {@link ProcessedCommandStore} is
 * supplied: a re-delivered command whose id was already recorded must not be handled a second time.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class CommandDispatcherTest {

    private ObjectMapper objectMapper;

    private SerializedDataTypeRegistry typeRegistry;

    private CommandAuthorizer authorizer;

    private Validator validator;

    private CommandHandlerRegistry commandHandlerRegistry;

    private ApplicationContext context;

    private ProcessedCommandStore store;

    private CommandHandler commandHandler;

    private CommandExecutionContext executionContext;

    private MyCommand cmd;

    @BeforeEach
    public void setup() throws Exception {
        objectMapper = mock(ObjectMapper.class);
        typeRegistry = mock(SerializedDataTypeRegistry.class);
        authorizer = mock(CommandAuthorizer.class);
        validator = mock(Validator.class);
        commandHandlerRegistry = mock(CommandHandlerRegistry.class);
        context = mock(ApplicationContext.class);
        store = mock(ProcessedCommandStore.class);
        commandHandler = mock(CommandHandler.class);
        executionContext = mock(CommandExecutionContext.class);
        cmd = new MyCommand();

        final ObjectWriter writer = mock(ObjectWriter.class);
        when(objectMapper.writer()).thenReturn(writer);
        when(writer.writeValueAsString(any())).thenReturn("ok");

        when(executionContext.getUser()).thenReturn(mock(User.class));
        when(typeRegistry.findClass(any())).thenReturn((Class) MyCommand.class);
        when(objectMapper.readValue(any(String.class), eq(MyCommand.class))).thenReturn(cmd);
        when(validator.validate(any())).thenReturn(Collections.emptySet());
        when(commandHandlerRegistry.findHandlerClass(any())).thenReturn((Class) CommandHandler.class);
        when(context.getBean((Class) any(Class.class))).thenReturn(commandHandler);
        when(authorizer.authorized(any(), any()))
                .thenReturn(new CommandAuthorizer.Result(true, cmd, null, List.of()));
        when(commandHandler.handle(any(), any())).thenReturn("ok");
    }

    @Test
    public void testDuplicateCommandHandledOnce() throws Exception {

        // PREPARE: dedup store reports "new" on the first delivery, "already processed" on the re-delivery
        when(store.processed(cmd.getEventId().asString())).thenReturn(false, true);
        final CommandDispatcher testee = new CommandDispatcher(objectMapper, typeRegistry, authorizer, validator,
                commandHandlerRegistry, context, store);

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
        final CommandDispatcher testee = new CommandDispatcher(objectMapper, typeRegistry, authorizer, validator,
                commandHandlerRegistry, context);

        // TEST
        testee.dispatch("MyCommand", "{}", executionContext, List.of());
        testee.dispatch("MyCommand", "{}", executionContext, List.of());

        // VERIFY: without a store every delivery is handled
        verify(commandHandler, times(2)).handle(any(), any());

    }

    /**
     * Minimal command with a stable event id for the round-trip test.
     */
    public static class MyCommand extends AbstractCommand {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final EventType TYPE = new EventType("MyCommand");

        public MyCommand() {
            super((EventId) null, (EventId) null);
        }

        @Override
        public EventType getEventType() {
            return TYPE;
        }

    }

}
