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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Authorizes commands against the permissions the application itself records, rather than against the roles
 * in the caller's token.
 * <p>
 * A role in the token cannot answer a question about a permission a customer defined after the release
 * shipped, which is why the decision comes from a {@link PermissionLookup} instead. The seam is unchanged:
 * the dispatcher still calls a {@link CommandAuthorizer} before the handler runs.
 */
@ThreadSafe
public final class PermissionCommandAuthorizer extends AbstractPermissionAuthorizer implements CommandAuthorizer {

    private final Function<Class<? extends Command>, String> permissionIdOf;

    /**
     * Constructor using the command's simple class name as its permission id.
     *
     * @param lookup       Answers what the caller holds.
     * @param bypassPolicy Roles that skip the check entirely.
     */
    public PermissionCommandAuthorizer(final PermissionLookup lookup, final PermissionBypassPolicy bypassPolicy) {
        this(lookup, bypassPolicy, Class::getSimpleName);
    }

    /**
     * Constructor with an explicit mapping from command type to permission id.
     * <p>
     * "The command's name is its permission id" is a convention of whatever generates the catalogue, not
     * something this class should assume, so it is a parameter. Returning {@literal null} from the function
     * denies the command - an operation nobody classified is not one to wave through.
     *
     * @param lookup         Answers what the caller holds.
     * @param bypassPolicy   Roles that skip the check entirely.
     * @param permissionIdOf Maps a command type to its catalogue id, or to {@literal null} if it has none.
     */
    public PermissionCommandAuthorizer(final PermissionLookup lookup, final PermissionBypassPolicy bypassPolicy,
                                       final Function<Class<? extends Command>, String> permissionIdOf) {
        super(lookup, bypassPolicy);
        this.permissionIdOf = Objects.requireNonNull(permissionIdOf, "permissionIdOf==null");
    }

    @Override
    public Result authorized(final Command command, final List<SimpleRole> userRoles) {
        // Without a context there is nobody to look up, so only a bypass role can get through. This overload
        // exists because the interface requires it; the dispatcher calls the three-argument one.
        return authorized(command, userRoles, null);
    }

    @Override
    public Result authorized(final Command command, final List<SimpleRole> userRoles,
                             @Nullable final ExecutionContext context) {
        Objects.requireNonNull(command, "command==null");
        final List<SimpleRole> roles = userRoles == null ? List.of() : userRoles;
        final String permissionId = permissionIdOf.apply(command.getClass());
        if (context == null) {
            return new Result(false, command, null, roles);
        }
        final boolean allowed = decide(context, roles, permissionId);
        // The allowed-roles slot carries the required permission, which is what actually decided this.
        final List<SimpleRole> required = permissionId == null ? null : List.of(new SimpleRole(permissionId));
        return new Result(allowed, command, required, roles);
    }

    /**
     * Returns the permission ids the caller holds. Exposed for diagnostics, never used to decide.
     *
     * @param context Who is calling.
     * @return Permission ids held, empty if not known.
     */
    public Set<String> permissionsOf(final ExecutionContext context) {
        return heldBy(context);
    }

}
