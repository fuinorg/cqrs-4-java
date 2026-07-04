/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.springboot.command.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.fuin.cqrs4j.core.*;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.UnauthorizedException;
import org.fuin.esc.api.Deserializer;
import org.fuin.esc.api.DeserializerRegistry;
import org.fuin.esc.api.EnhancedMimeType;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles incoming commands, validates them, verifies if the current user is authorized to execute it
 * and dispatches them to the appropriate command handlers. This object is meant be used in command
 * controllers and avoids duplicating the dispatch code in each and every controller.
 */
@ThreadSafe
public class CommandDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(CommandDispatcher.class);

    /** Benign empty result returned when a duplicate (already-processed) command is skipped. */
    private static final String DUPLICATE_RESULT_JSON = "{}";

    private final ObjectMapper objectMapper;

    private final DeserializerRegistry deserializerRegistry;

    private final CommandAuthorizer authorizer;

    private final Validator validator;

    private final CommandHandlerRegistry commandHandlerRegistry;

    private final ApplicationContext context;

    @Nullable
    private final ProcessedCommandStore processedCommandStore;

    /**
     * Constructor with all mandatory collaborators (command deduplication disabled).
     *
     * @param objectMapper           Serializes the command-handler result to JSON.
     * @param deserializerRegistry   Deserializes (and up-casts) the command JSON by {@code (type, version)}.
     * @param authorizer             Decides if the current user may execute a command.
     * @param validator              Validates the deserialized command.
     * @param commandHandlerRegistry Resolves the handler class for a given command class.
     * @param context                Spring context used to look up the command handler bean.
     */
    public CommandDispatcher(ObjectMapper objectMapper,
                             DeserializerRegistry deserializerRegistry,
                             CommandAuthorizer authorizer,
                             Validator validator,
                             CommandHandlerRegistry commandHandlerRegistry,
                             ApplicationContext context) {
        this(objectMapper, deserializerRegistry, authorizer, validator, commandHandlerRegistry, context, null);
    }

    /**
     * Constructor with an optional processed-command store for effectively-once command receipt. When a store is
     * supplied, a command whose id is already recorded is skipped instead of being handled again, and a
     * successfully handled command's id is recorded afterwards (record-after-success). Passing {@literal null}
     * disables deduplication.
     *
     * @param objectMapper           Serializes the command-handler result to JSON.
     * @param deserializerRegistry   Deserializes (and up-casts) the command JSON by {@code (type, version)}.
     * @param authorizer             Decides if the current user may execute a command.
     * @param validator              Validates the deserialized command.
     * @param commandHandlerRegistry Resolves the handler class for a given command class.
     * @param context                Spring context used to look up the command handler bean.
     * @param processedCommandStore  Optional dedup store; {@literal null} disables deduplication.
     */
    public CommandDispatcher(ObjectMapper objectMapper,
                             DeserializerRegistry deserializerRegistry,
                             CommandAuthorizer authorizer,
                             Validator validator,
                             CommandHandlerRegistry commandHandlerRegistry,
                             ApplicationContext context,
                             @Nullable ProcessedCommandStore processedCommandStore) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper==null");
        this.deserializerRegistry = Objects.requireNonNull(deserializerRegistry, "deserializerRegistry==null");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer==null");
        this.validator = Objects.requireNonNull(validator, "validator==null");
        this.commandHandlerRegistry = Objects.requireNonNull(commandHandlerRegistry, "commandHandlerRegistry==null");
        this.context = Objects.requireNonNull(context, "context==null");
        this.processedCommandStore = processedCommandStore;
    }

    /**
     * Deserializes a command and forwards it to the appropriate command handler.
     *
     * @param cmdType          Unique type name of the command to deserialize.
     * @param contentType      Content type the command was serialized with ({@literal null} falls back to the
     *                         registry default); the command is deserialized by its media type (base type,
     *                         encoding and version) and up-cast to the local latest representation, so a rolling
     *                         deploy can accept older command versions and any registered format.
     * @param cmdJson          Serialized command.
     * @param executionContext Context of the user executing the command (tenant and user information).
     * @param userRoles        Roles of the current user used by the security filter.
     * @return Result to return.
     * @throws CommandExecutionFailedException Something went wrong during command dispatching or execution.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String dispatch(String cmdType, @Nullable String contentType, String cmdJson, CommandExecutionContext executionContext,
                           List<SimpleRole> userRoles) throws CommandExecutionFailedException {
        final Object obj = deserialize(cmdType, contentType, cmdJson);
        LOG.debug("User '{}' posted: {}", executionContext.getUser().getUserId(), obj.getClass().getSimpleName());
        final Set<ConstraintViolation<Object>> violations = validator.validate(obj);
        if (!violations.isEmpty()) {
            final String message = "Object is not valid (" + cmdType + "): " + cmdJson;
            LOG.error("{} - Class: {}", message, obj.getClass().getName());
            throw new ConstraintViolationException(message, violations);
        }
        if (obj instanceof Command cmd) {
            final Class<? extends CommandHandler> commandHandlerClass = commandHandlerRegistry.findHandlerClass(cmd.getClass());
            final CommandHandler commandHandler = context.getBean(commandHandlerClass);
            final CommandAuthorizer.Result authResult  = authorizer.authorized(cmd, userRoles);
            if (!authResult.success()) {
                LOG.error("User '{}' not authorized! {}", executionContext.getUser().getUserId(), authResult.getMessage());
                throw new UnauthorizedException();
            }
            final String commandId = cmd.getEventId().asString();
            if (processedCommandStore != null && processedCommandStore.processed(commandId)) {
                // Effectively-once: a re-delivered command that was already handled is skipped. The original
                // result is not retained, so a benign empty result is returned to acknowledge the delivery.
                LOG.debug("Skipping already-processed command '{}' ({})", commandId, cmd.getClass().getSimpleName());
                return DUPLICATE_RESULT_JSON;
            }
            final Object result = commandHandler.handle(executionContext, cmd);
            if (processedCommandStore != null) {
                // Record-after-success: only mark once the handler completed without error.
                processedCommandStore.markProcessed(commandId);
            }
            final String json = writeJson(result);
            LOG.info("Result: {}", json);
            return json;
        } else {
            final String message = "Not a command: {}" + obj.getClass().getName();
            LOG.error("{}", message);
            throw new ConstraintViolationException("Not a command (" + cmdType + "): " + cmdJson);
        }
    }

    private Object deserialize(String cmdType, @Nullable String contentType, String cmdJson) {
        final SerializedDataType type = new SerializedDataType(cmdType);
        final EnhancedMimeType mimeType = mimeType(contentType);
        final Charset encoding = mimeType.getEncoding() == null ? StandardCharsets.UTF_8 : mimeType.getEncoding();
        try {
            final Deserializer deserializer = deserializerRegistry.getDeserializer(type, mimeType);
            return deserializer.unmarshal(cmdJson.getBytes(encoding), type, mimeType);
        } catch (final RuntimeException ex) {
            final String message = "Failed to deserialize command (" + cmdType
                    + (contentType == null ? "" : ", contentType=" + contentType) + "): " + cmdJson;
            LOG.error(message, ex);
            throw new ConstraintViolationException(message);
        }
    }

    /**
     * Derives the deserializer lookup mime type from the request's content type. The base type and version come
     * from the caller (so the path is not JSON-locked); the encoding falls back to the registry default when the
     * request omits it, and a {@literal null} content type falls back to the registry default mime type entirely.
     */
    private EnhancedMimeType mimeType(@Nullable String contentType) {
        final EnhancedMimeType defaultMimeType = Objects.requireNonNull(deserializerRegistry.getDefaultMimeType(),
                "deserializerRegistry.getDefaultMimeType()");
        if (contentType == null) {
            return defaultMimeType;
        }
        final EnhancedMimeType requested = Objects.requireNonNull(EnhancedMimeType.create(contentType),
                "EnhancedMimeType.create(contentType)");
        final Charset encoding = requested.getEncoding() != null ? requested.getEncoding() : defaultMimeType.getEncoding();
        return EnhancedMimeType.create(requested.getPrimaryType(), requested.getSubType(), encoding, requested.getVersion());
    }

    /**
     * Serializes the given object to a JSON string.
     *
     * @param obj Object to serialize.
     * @return JSON representation of the object.
     */
    public String writeJson(Object obj) {
        try {
            return objectMapper.writer().writeValueAsString(obj);
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write JSON of: " + obj, ex);
        }
    }

}
