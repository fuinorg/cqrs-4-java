package org.fuin.cqrs4j.springboot.test.cmd;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.fuin.cqrs4j.core.CommandAuthorizer;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandHandlerRegistry;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.fuin.cqrs4j.springboot.command.core.CommandDispatcher;
import org.fuin.cqrs4j.springboot.command.core.QryProcessedCommandStore;
import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.esc.api.SimpleSerializedDataTypeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker-free end-to-end test for effectively-once command receipt. It boots a minimal receiver slice (generic
 * {@link CommandController} + {@link CommandDispatcher} + real {@link QryProcessedCommandStore} on an embedded
 * HSQLDB) and posts the <b>same</b> command (identical {@code event-id}) twice. The observable side effect
 * ({@link GreetingRecorder}) must fire only once, proving the re-delivery was deduplicated.
 */
@SpringBootTest(classes = CommandDedupTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:hsqldb:mem:cmd-dedup-test",
                "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureMockMvc
class CommandDedupTest {

    private static final String EVENT_ID = "f910c6d7-debc-46e1-ae02-9ca6f4658cf5";

    private static final String COMMAND_JSON = "{\"event-id\":\"" + EVENT_ID + "\",\"name\":\"World\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GreetingRecorder recorder;

    @Test
    void duplicateCommandIsHandledOnce() throws Exception {

        // TEST: deliver the identical command twice
        mockMvc.perform(post("/cmd/SampleGreetCommand")
                        .contentType(MediaType.APPLICATION_JSON).content(COMMAND_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(post("/cmd/SampleGreetCommand")
                        .contentType(MediaType.APPLICATION_JSON).content(COMMAND_JSON))
                .andExpect(status().isOk());

        // VERIFY: the handler side effect fired exactly once
        assertThat(recorder.count()).isEqualTo(1);
        assertThat(recorder.lastGreeting()).isEqualTo("Hello, World");
    }

    /**
     * Minimal receiver slice: embedded HSQLDB + JPA (for the processed-command table), the dedup store, a
     * dispatcher wired with it, and the generic controller plus the sample command handler.
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            ValidationAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class
    })
    @EntityScan("org.fuin.cqrs4j.jpa.command")
    static class TestApp {

        @Bean
        GreetingRecorder greetingRecorder() {
            return new GreetingRecorder();
        }

        @Bean
        SampleGreetCommandHandler sampleGreetCommandHandler(final GreetingRecorder recorder) {
            return new SampleGreetCommandHandler(recorder);
        }

        @Bean
        QryProcessedCommandStore processedCommandStore() {
            return new QryProcessedCommandStore();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                    .registerModule(new Ddd4JacksonModule(new NoOpEntityIdFactory()));
        }

        @Bean
        SerializedDataTypeRegistry serializedDataTypeRegistry() {
            return new SimpleSerializedDataTypeRegistry.Builder()
                    .add(SampleGreetCommand.SER_TYPE, SampleGreetCommand.class)
                    .build();
        }

        @Bean
        CommandHandlerRegistry commandHandlerRegistry() {
            return cmdClass -> SampleGreetCommandHandler.class;
        }

        @Bean
        CommandAuthorizer commandAuthorizer() {
            return new PermitAllCommandAuthorizer();
        }

        @Bean
        CommandExecutionContext commandExecutionContext() {
            return new FixedCommandExecutionContext();
        }

        @Bean
        CommandDispatcher commandDispatcher(final ObjectMapper objectMapper,
                                            final SerializedDataTypeRegistry typeRegistry,
                                            final CommandAuthorizer authorizer,
                                            final Validator validator,
                                            final CommandHandlerRegistry commandHandlerRegistry,
                                            final ApplicationContext context,
                                            final ProcessedCommandStore processedCommandStore) {
            return new CommandDispatcher(objectMapper, typeRegistry, authorizer, validator,
                    commandHandlerRegistry, context, processedCommandStore);
        }

        @Bean
        CommandController commandController(final CommandDispatcher dispatcher,
                                            final CommandExecutionContext executionContext) {
            return new CommandController(dispatcher, executionContext);
        }

    }

    /**
     * Entity-id factory that supports no entity ids (the sample command carries none).
     */
    private static final class NoOpEntityIdFactory implements EntityIdFactory {

        @Override
        public boolean containsType(final String type) {
            return false;
        }

        @Override
        public boolean isValid(final String type, final String id) {
            return false;
        }

        @Override
        public EntityId createEntityId(final String type, final String id) {
            throw new IllegalArgumentException("Unsupported entity id type: " + type);
        }

    }

}
