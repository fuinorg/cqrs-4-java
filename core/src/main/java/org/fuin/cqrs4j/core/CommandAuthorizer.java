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

import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Decides if a user has the rights to execute a command.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface CommandAuthorizer {

    /**
     * Determines if a user is authorized to execute the given command.
     *
     * @param command   Command to execute.
     * @param userRoles Security roles the user has assigned.
     * @return Result of the verification.
     */
    Result authorized(Command command, List<SimpleRole> userRoles);

    /**
     * Determines if a user is authorized to execute the given command, with the execution context available.
     * <p>
     * An implementation that decides from the roles in the token alone needs nothing beyond
     * {@link #authorized(Command, List)}, which is why that method stays the one that must be implemented.
     * An implementation that decides from the application's own data instead needs to know <em>who</em> is
     * calling - the subject and the tenant - and the dispatcher already holds that as the context it passes
     * to the handler. This overload is how it reaches the authorizer.
     * <p>
     * The default delegates, so existing implementations keep working unchanged.
     *
     * @param command   Command to execute.
     * @param userRoles Security roles the user has assigned.
     * @param context   Who is calling.
     * @return Result of the verification.
     */
    default Result authorized(Command command, List<SimpleRole> userRoles, ExecutionContext context) {
        return authorized(command, userRoles);
    }

    /**
     * Result of the authorization check.
     *
     * @param success If the user is authorized to execute the command {@literal true}.
     * @param command Command that should be executed.
     * @param allowedRoles Roles allowed to execute the command.
     * @param userRoles Roles the user has.
     */
    record Result(boolean success, Command command, @Nullable List<SimpleRole> allowedRoles, List<SimpleRole> userRoles) {

        /**
         * Returns a message for the result.
         *
         * @return Result information.
         */
        public String getMessage() {
            final String result;
            if (success) {
                result = "Authorization for command " + command.getClass().getSimpleName() + " successfully verified";
            } else {
                result = "Authorization for command " + command.getClass().getSimpleName() + " failed";
            }
            final String allowedRolesStr;
            if (allowedRoles == null) {
                allowedRolesStr = "NONE";
            } else {
                allowedRolesStr = allowedRoles.stream().map(SimpleRole::toString)
                        .collect(Collectors.joining(", ", "[", "]"));
            }
            final String userRolesStr = userRoles.stream().map(SimpleRole::toString)
                    .collect(Collectors.joining(", ", "[", "]"));
            return result + " - Allowed roles: " + allowedRolesStr + " / User's roles: " + userRolesStr;
        }

    }

}
