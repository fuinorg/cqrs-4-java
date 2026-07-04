package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.enterprise.inject.Instance;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandAuthorizer;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.CommandHandlerRegistry;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.UnauthorizedException;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles incoming commands, validates them, verifies if the current user is authorized to execute it and
 * dispatches them to the appropriate command handlers. This is the Quarkus counterpart of the Spring
 * {@code CommandDispatcher}: it uses JSON-B ({@link Jsonb}) instead of a Jackson {@code ObjectMapper} and resolves
 * the handler bean via a CDI {@link Instance} lookup instead of a Spring {@code ApplicationContext}. Everything
 * else - type resolution, validation, authorization, and the CQRS-4 effectively-once deduplication - is the same
 * neutral pipeline. It is meant to be used in command resources and avoids duplicating the dispatch code.
 */
@ThreadSafe
public class QuarkusCommandDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusCommandDispatcher.class);

    /** Benign empty result returned when a duplicate (already-processed) command is skipped. */
    private static final String DUPLICATE_RESULT_JSON = "{}";

    private final Jsonb jsonb;

    private final SerializedDataTypeRegistry typeRegistry;

    private final CommandAuthorizer authorizer;

    private final Validator validator;

    private final CommandHandlerRegistry commandHandlerRegistry;

    private final Instance<CommandHandler> commandHandlers;

    @Nullable
    private final ProcessedCommandStore processedCommandStore;

    /**
     * Constructor with all mandatory collaborators (command deduplication disabled).
     *
     * @param jsonb                  Maps the command JSON to/from objects.
     * @param typeRegistry           Resolves a command type name to the concrete command class.
     * @param authorizer             Decides if the current user may execute a command.
     * @param validator              Validates the deserialized command.
     * @param commandHandlerRegistry Resolves the handler class for a given command class.
     * @param commandHandlers        CDI instance used to look up the command handler bean.
     */
    public QuarkusCommandDispatcher(final Jsonb jsonb,
                                    final SerializedDataTypeRegistry typeRegistry,
                                    final CommandAuthorizer authorizer,
                                    final Validator validator,
                                    final CommandHandlerRegistry commandHandlerRegistry,
                                    final Instance<CommandHandler> commandHandlers) {
        this(jsonb, typeRegistry, authorizer, validator, commandHandlerRegistry, commandHandlers, null);
    }

    /**
     * Constructor with an optional processed-command store for effectively-once command receipt. When a store is
     * supplied, a command whose id is already recorded is skipped instead of being handled again, and a
     * successfully handled command's id is recorded afterwards (record-after-success). Passing {@literal null}
     * disables deduplication.
     *
     * @param jsonb                  Maps the command JSON to/from objects.
     * @param typeRegistry           Resolves a command type name to the concrete command class.
     * @param authorizer             Decides if the current user may execute a command.
     * @param validator              Validates the deserialized command.
     * @param commandHandlerRegistry Resolves the handler class for a given command class.
     * @param commandHandlers        CDI instance used to look up the command handler bean.
     * @param processedCommandStore  Optional dedup store; {@literal null} disables deduplication.
     */
    public QuarkusCommandDispatcher(final Jsonb jsonb,
                                    final SerializedDataTypeRegistry typeRegistry,
                                    final CommandAuthorizer authorizer,
                                    final Validator validator,
                                    final CommandHandlerRegistry commandHandlerRegistry,
                                    final Instance<CommandHandler> commandHandlers,
                                    @Nullable final ProcessedCommandStore processedCommandStore) {
        this.jsonb = Objects.requireNonNull(jsonb, "jsonb==null");
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry==null");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer==null");
        this.validator = Objects.requireNonNull(validator, "validator==null");
        this.commandHandlerRegistry = Objects.requireNonNull(commandHandlerRegistry, "commandHandlerRegistry==null");
        this.commandHandlers = Objects.requireNonNull(commandHandlers, "commandHandlers==null");
        this.processedCommandStore = processedCommandStore;
    }

    /**
     * Deserializes a command and forwards it to the appropriate command handler.
     *
     * @param cmdType          Unique type name of the command to deserialize.
     * @param cmdJson          Command JSON.
     * @param executionContext Context of the user executing the command (tenant and user information).
     * @param userRoles        Roles of the current user used by the security filter.
     * @return Result to return.
     * @throws CommandExecutionFailedException Something went wrong during command dispatching or execution.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String dispatch(final String cmdType, final String cmdJson, final CommandExecutionContext executionContext,
                           final List<SimpleRole> userRoles) throws CommandExecutionFailedException {
        final Class<?> objClass = findType(cmdType);
        LOG.debug("User '{}' posted: {}", executionContext.getUser().getUserId(), objClass.getSimpleName());
        final Object obj = parseJson(cmdJson, objClass);
        final Set<ConstraintViolation<Object>> violations = validator.validate(obj);
        if (!violations.isEmpty()) {
            final String message = "Object is not valid (" + cmdType + "): " + cmdJson;
            LOG.error("{} - Class: {}", message, obj.getClass().getName());
            throw new ConstraintViolationException(message, violations);
        }
        if (obj instanceof Command cmd) {
            final Class<? extends CommandHandler> commandHandlerClass =
                    commandHandlerRegistry.findHandlerClass((Class<? extends Command>) objClass);
            final CommandHandler commandHandler = commandHandlers.select(commandHandlerClass).get();
            final CommandAuthorizer.Result authResult = authorizer.authorized(cmd, userRoles);
            if (!authResult.success()) {
                LOG.error("User '{}' not authorized! {}", executionContext.getUser().getUserId(), authResult.getMessage());
                throw new UnauthorizedException();
            }
            final String commandId = cmd.getEventId().asString();
            if (processedCommandStore != null && processedCommandStore.processed(commandId)) {
                // Effectively-once: a re-delivered command that was already handled is skipped. The original
                // result is not retained, so a benign empty result is returned to acknowledge the delivery.
                LOG.debug("Skipping already-processed command '{}' ({})", commandId, objClass.getSimpleName());
                return DUPLICATE_RESULT_JSON;
            }
            final Object result = commandHandler.handle(executionContext, cmd);
            if (processedCommandStore != null) {
                // Record-after-success: only mark once the handler completed without error.
                processedCommandStore.markProcessed(commandId);
            }
            final String json = jsonb.toJson(result);
            LOG.info("Result: {}", json);
            return json;
        } else {
            LOG.error("Not a command: {}", obj.getClass().getName());
            throw new ConstraintViolationException("Not a command (" + cmdType + "): " + cmdJson);
        }
    }

    private Object parseJson(final String json, final Class<?> objClass) {
        try {
            return jsonb.fromJson(json, objClass);
        } catch (final JsonbException ex) {
            LOG.error("Failed to parse JSON: {}", json, ex);
            throw new ConstraintViolationException("Failed to parse JSON: " + json);
        }
    }

    private Class<?> findType(final String typeName) {
        try {
            return typeRegistry.findClass(new SerializedDataType(typeName));
        } catch (final RuntimeException ex) {
            throw new IllegalArgumentException("Could not find class for type: " + typeName, ex);
        }
    }

}
