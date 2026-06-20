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
import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.CommandHandlerRegistry;
import org.fuin.cqrs4j.core.CommandSecurityFilter;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.UnauthorizedException;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles incoming commands and dispatches them to the appropriate command handlers.
 * Helper class that can be used in command controllers and avoids duplicating the
 * dispatch code in each and every controller.
 */
public class CommandDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(CommandDispatcher.class);

    private final ObjectMapper objectMapper;

    private final SerializedDataTypeRegistry typeRegistry;

    private final CommandSecurityFilter commandSecurityFilter;

    private final Validator validator;

    private final CommandHandlerRegistry commandHandlerRegistry;

    private final ApplicationContext context;

    /**
     * Constructor with all mandatory collaborators.
     *
     * @param objectMapper           Maps the command JSON to/from objects.
     * @param typeRegistry           Resolves a command type name to the concrete command class.
     * @param commandSecurityFilter  Decides if the current user may execute a command.
     * @param validator              Validates the deserialized command.
     * @param commandHandlerRegistry Resolves the handler class for a given command class.
     * @param context                Spring context used to look up the command handler bean.
     */
    public CommandDispatcher(ObjectMapper objectMapper,
                             SerializedDataTypeRegistry typeRegistry,
                             CommandSecurityFilter commandSecurityFilter,
                             Validator validator,
                             CommandHandlerRegistry commandHandlerRegistry,
                             ApplicationContext context) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper==null");
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry==null");
        this.commandSecurityFilter = Objects.requireNonNull(commandSecurityFilter, "commandSecurityFilter==null");
        this.validator = Objects.requireNonNull(validator, "validator==null");
        this.commandHandlerRegistry = Objects.requireNonNull(commandHandlerRegistry, "commandHandlerRegistry==null");
        this.context = Objects.requireNonNull(context, "context==null");
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
    public String dispatch(String cmdType, String cmdJson, CommandExecutionContext executionContext,
                           List<SimpleRole> userRoles) throws CommandExecutionFailedException {
        final Class<?> objClass = findType(cmdType);
        final String username = executionContext.getUser().getUserName();
        LOG.info("User '{}' posted {}: {}", username, objClass.getSimpleName(), cmdJson);
        final Object obj = parseJson(cmdJson, objClass);
        final Set<ConstraintViolation<Object>> violations = validator.validate(obj);
        if (!violations.isEmpty()) {
            final String message = "Object is not valid (" + cmdType + "): " + cmdJson;
            LOG.error("{} - Class: {}", message, obj.getClass().getName());
            throw new ConstraintViolationException(message, violations);
        }
        if (obj instanceof Command cmd) {
            final Class<? extends CommandHandler> commandHandlerClass = commandHandlerRegistry.findHandlerClass((Class<? extends Command>) objClass);
            final CommandHandler commandHandler = context.getBean(commandHandlerClass);
            if (!commandSecurityFilter.authorized(cmd, userRoles)) {
                LOG.error("User '{}' is not authorized to execute: {}", username, objClass.getSimpleName());
                throw new UnauthorizedException();
            }
            final Object result = commandHandler.handle(executionContext, cmd);
            final String json = writeJson(result);
            LOG.info("Result: {}", json);
            return json;
        } else {
            final String message = "Not a command: {}" + obj.getClass().getName();
            LOG.error("{}", message);
            throw new ConstraintViolationException("Not a command (" + cmdType + "): " + cmdJson);
        }
    }

    private Object parseJson(String json, Class<?> objClass) {
        try {
            return objectMapper.readValue(json, objClass);
        } catch (JsonProcessingException ex) {
            final String message = "Failed to parse JSON: " + json;
            LOG.error(message, ex);
            throw new ConstraintViolationException(message);
        }
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

    private Class<?> findType(String typeName) {
        try {
            return typeRegistry.findClass(new SerializedDataType(typeName));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Could not find class for type: " + typeName, ex);
        }
    }

}
