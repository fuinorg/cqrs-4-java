package org.fuin.cqrs4j.springboot.command.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.validation.Validator;
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandAuthorizer;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.CommandHandlerRegistry;
import org.fuin.cqrs4j.jackson.AbstractCommand;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.User;
import org.fuin.esc.api.Converter;
import org.fuin.esc.api.ConverterRegistry;
import org.fuin.esc.api.Deserializer;
import org.fuin.esc.api.DeserializerRegistry;
import org.fuin.esc.api.EnhancedMimeType;
import org.fuin.esc.api.SerDeserializerRegistry;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.esc.api.SimpleConverterRegistry;
import org.fuin.esc.api.SimpleSerializerDeserializerRegistry;
import org.fuin.esc.api.UpcastingDeserializerRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CQRS-3 acceptance: a command posted at an <b>older</b> version is deserialized by {@code (type, version)} and
 * up-cast to the receiver's latest representation before it reaches the handler — the weak-schema behaviour a
 * rolling deploy relies on. Uses the real esc {@link UpcastingDeserializerRegistry} + {@link ConverterRegistry},
 * exactly like the event read path; only the surrounding collaborators are mocked.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class CommandVersioningTest {

    @Test
    public void v1CommandIsUpcastToV2AtReceiver() throws Exception {

        // PREPARE: a v1 deserializer (type=GreetCommand, version=1 -> GreetCommandV1) and a v1->v2 up-caster,
        // wired into an UpcastingDeserializerRegistry (the same seam the event read path uses).
        final SerializedDataType type = new SerializedDataType("GreetCommand");
        final Deserializer v1Deserializer = new Deserializer() {
            @Override
            public <T> T unmarshal(final Object data, final SerializedDataType t, final EnhancedMimeType m) {
                return (T) new GreetCommandV1("World");
            }
        };
        final SerDeserializerRegistry serDe = new SimpleSerializerDeserializerRegistry.Builder(
                EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .add(type, v1Deserializer, EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8, "1"))
                .build();
        final Converter<GreetCommandV1, GreetCommandV2> upcaster = new Converter<>() {
            @Override
            public Class<GreetCommandV1> getSourceType() {
                return GreetCommandV1.class;
            }
            @Override
            public Class<GreetCommandV2> getTargetType() {
                return GreetCommandV2.class;
            }
            @Override
            public GreetCommandV2 convert(final GreetCommandV1 source) {
                return new GreetCommandV2("Hello, " + source.getName());
            }
        };
        final ConverterRegistry converters = new SimpleConverterRegistry.Builder()
                .add(type, "1", "2", upcaster)
                .build();
        final DeserializerRegistry deserializerRegistry = new UpcastingDeserializerRegistry(serDe, converters);

        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        final ObjectWriter writer = mock(ObjectWriter.class);
        when(objectMapper.writer()).thenReturn(writer);
        final CommandAuthorizer authorizer = mock(CommandAuthorizer.class);
        when(authorizer.authorized(any(), any()))
                .thenReturn(new CommandAuthorizer.Result(true, new GreetCommandV2("x"), null, List.of()));
        final Validator validator = mock(Validator.class);
        when(validator.validate(any())).thenReturn(Collections.emptySet());
        final CommandHandlerRegistry handlerRegistry = mock(CommandHandlerRegistry.class);
        when(handlerRegistry.findHandlerClass(any())).thenReturn((Class) CommandHandler.class);
        final CommandHandler handler = mock(CommandHandler.class);
        final ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean((Class) any(Class.class))).thenReturn(handler);
        final CommandExecutionContext execCtx = mock(CommandExecutionContext.class);
        when(execCtx.getUser()).thenReturn(mock(User.class));

        final CommandDispatcher testee = new CommandDispatcher(objectMapper, deserializerRegistry, authorizer,
                validator, handlerRegistry, context);

        // TEST: deliver a v1 command (Content-Type: application/json;version=1)
        testee.dispatch("GreetCommand", "1", "{\"name\":\"World\"}", execCtx, List.of());

        // VERIFY: the handler received the UP-CAST v2 representation, not the v1 one
        final ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(handler).handle(any(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(GreetCommandV2.class);
        assertThat(((GreetCommandV2) captor.getValue()).getGreeting()).isEqualTo("Hello, World");
    }

    /**
     * Version 1 of the greet command (carries the raw name).
     */
    public static class GreetCommandV1 extends AbstractCommand {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final EventType TYPE = new EventType("GreetCommand");

        private final String name;

        public GreetCommandV1(final String name) {
            super((EventId) null, (EventId) null);
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return "1";
        }

        @Override
        public EventType getEventType() {
            return TYPE;
        }

    }

    /**
     * Version 2 of the greet command (carries the fully rendered greeting).
     */
    public static class GreetCommandV2 extends AbstractCommand {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final EventType TYPE = new EventType("GreetCommand");

        private final String greeting;

        public GreetCommandV2(final String greeting) {
            super((EventId) null, (EventId) null);
            this.greeting = greeting;
        }

        public String getGreeting() {
            return greeting;
        }

        @Override
        public EventType getEventType() {
            return TYPE;
        }

    }

}
