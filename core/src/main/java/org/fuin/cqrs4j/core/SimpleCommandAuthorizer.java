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
package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.*;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Static mapping from commands to allowed roles.
 */
@ThreadSafe
public final class SimpleCommandAuthorizer implements CommandAuthorizer {

    // private final Map<Class<? extends Command>, List<SecurityRole>> commandRoleMap;
    private final CommandRoleService commandRoleService;

    /**
     * Constructor with the command-to-roles mapping.
     *
     * @param commandRoleService Service returning roles allowed to execute a command. An empty
     *                           list means everyone is allowed, a missing entry means access is denied.
     */
    public SimpleCommandAuthorizer(CommandRoleService commandRoleService) {
        this.commandRoleService = Objects.requireNonNull(commandRoleService, "roleService==null");
    }

    @Override
    public boolean authorized(Command command, List<SimpleRole> userRoles) {
        final Optional<List<SecurityRole>> result = commandRoleService.readAllowedRoles(command.getClass());
        if (result.isEmpty()) {
            // Assume that we forgot to configure a role and deny access
            return false;
        }
        final List<SecurityRole> commandRoles = result.get();
        if (commandRoles.isEmpty()) {
            // No roles means that everyone can access the command
            return true;
        }

        final List<SimpleRole> allowedRoles = commandRoles.stream()
                .map(role -> map2SimpleRole(role, command))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        // At least one role of the user needs to match
        for (final SimpleRole userRole : userRoles) {
            for (final SimpleRole allowedRole : allowedRoles) {
                if (userRole.equals(allowedRole)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<SimpleRole> map2SimpleRole(SecurityRole role, Command command) {
        if (role instanceof SimpleRole sr) {
            return Optional.of(sr);
        }
        if (command instanceof AggregateCommand<?, ?> ac && role instanceof EntityRole er) {
            final EntityIdPath entityIdPath = ac.getEntityIdPath();
            return Optional.of(new EntityRoleInstance(entityIdPath, er).asSimpleRole());
        }
        return Optional.empty();
    }

    /**
     * Provides the necessary roles to check permissions.
     */
    public interface CommandRoleService {

        /**
         * Returns a list of roles for a given command type.
         *
         * @param cmdClass Type of command to return roles that are allowed to execute the command.
         * @return All roles that are allowed to execute the command.
         * An empty list means everyone is allowed to execute the command.
         * An empty value means no one is allowed (all access denied).
         * In that case most likely the config is incomplete and nothing is configured for that command.
         */
        Optional<List<SecurityRole>> readAllowedRoles(Class<? extends Command> cmdClass);

    }

}
