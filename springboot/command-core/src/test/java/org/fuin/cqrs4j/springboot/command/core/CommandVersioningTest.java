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
import org.junit.jupiter.api.BeforeEach;
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
 * CQRS-3 acceptance for the format-agnostic command path. A command is deserialized purely by the media type of
 * its {@code Content-Type} (base type, encoding and version) through the real esc
 * {@link UpcastingDeserializerRegistry} + {@link ConverterRegistry} — the same seam the event read path uses —
 * and up-cast to the receiver's latest representation. Neither the version nor the format (JSON, XML, …) is
 * hardcoded.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class CommandVersioningTest {

    private static final SerializedDataType TYPE = new SerializedDataType("GreetCommand");

    private ObjectMapper objectMapper;
    private CommandAuthorizer authorizer;
    private Validator validator;
    private CommandHandlerRegistry handlerRegistry;
    private CommandHandler handler;
    private ApplicationContext context;
    private CommandExecutionContext execCtx;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        final ObjectWriter writer = mock(ObjectWriter.class);
        when(objectMapper.writer()).thenReturn(writer);
        authorizer = mock(CommandAuthorizer.class);
        when(authorizer.authorized(any(), any()))
                .thenReturn(new CommandAuthorizer.Result(true, new GreetCommandV2("x"), null, List.of()));
        validator = mock(Validator.class);
        when(validator.validate(any())).thenReturn(Collections.emptySet());
        handlerRegistry = mock(CommandHandlerRegistry.class);
        when(handlerRegistry.findHandlerClass(any())).thenReturn((Class) CommandHandler.class);
        handler = mock(CommandHandler.class);
        context = mock(ApplicationContext.class);
        when(context.getBean((Class) any(Class.class))).thenReturn(handler);
        execCtx = mock(CommandExecutionContext.class);
        when(execCtx.getUser()).thenReturn(mock(User.class));
    }

    private CommandDispatcher dispatcher(final DeserializerRegistry deserializerRegistry) {
        return new CommandDispatcher(objectMapper, deserializerRegistry, authorizer, validator, handlerRegistry, context);
    }

    private Command handledCommand() throws Exception {
        final ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(handler).handle(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    public void v1CommandIsUpcastToV2() throws Exception {

        // PREPARE: a v1 JSON deserializer + a v1->v2 up-caster, wired into an UpcastingDeserializerRegistry.
        final Deserializer v1 = new Deserializer() {
            @Override
            public <T> T unmarshal(final Object data, final SerializedDataType t, final EnhancedMimeType m) {
                return (T) new GreetCommandV1("World");
            }
        };
        final SerDeserializerRegistry serDe = new SimpleSerializerDeserializerRegistry.Builder(
                EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .add(TYPE, v1, EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8, "1"))
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
        final ConverterRegistry converters = new SimpleConverterRegistry.Builder().add(TYPE, "1", "2", upcaster).build();

        // TEST: deliver a v1 command
        dispatcher(new UpcastingDeserializerRegistry(serDe, converters))
                .dispatch("GreetCommand", "application/json;version=1", "{\"name\":\"World\"}", execCtx, List.of());

        // VERIFY: the handler received the up-cast v2 representation
        assertThat(handledCommand()).isInstanceOf(GreetCommandV2.class);
        assertThat(((GreetCommandV2) handledCommand()).getGreeting()).isEqualTo("Hello, World");
    }

    @Test
    public void nonJsonContentTypeSelectsItsOwnDeserializer() throws Exception {

        // PREPARE: ONLY an application/xml deserializer is registered (no JSON one), so selection must be driven
        // purely by the request's Content-Type base type — a hardcoded "application/json" would find nothing.
        final Deserializer xml = new Deserializer() {
            @Override
            public <T> T unmarshal(final Object data, final SerializedDataType t, final EnhancedMimeType m) {
                return (T) new GreetCommandV2("from-xml");
            }
        };
        final SerDeserializerRegistry serDe = new SimpleSerializerDeserializerRegistry.Builder(
                EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .add(TYPE, xml, EnhancedMimeType.create("application", "xml", StandardCharsets.UTF_8, "1"))
                .build();
        final ConverterRegistry converters = new SimpleConverterRegistry.Builder().build();

        // TEST: deliver an application/xml command
        dispatcher(new UpcastingDeserializerRegistry(serDe, converters))
                .dispatch("GreetCommand", "application/xml;version=1", "<greet/>", execCtx, List.of());

        // VERIFY: the XML deserializer was selected from the content type
        assertThat(((GreetCommandV2) handledCommand()).getGreeting()).isEqualTo("from-xml");
    }

    /**
     * Version 1 of the greet command (carries the raw name).
     */
    public static class GreetCommandV1 extends AbstractCommand {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final EventType TYPE_V1 = new EventType("GreetCommand");

        private final String name;

        public GreetCommandV1(final String name) {
            super((EventId) null, (EventId) null);
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public EventType getEventType() {
            return TYPE_V1;
        }

    }

    /**
     * Version 2 of the greet command (carries the fully rendered greeting).
     */
    public static class GreetCommandV2 extends AbstractCommand {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final EventType TYPE_V2 = new EventType("GreetCommand");

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
            return TYPE_V2;
        }

    }

}
