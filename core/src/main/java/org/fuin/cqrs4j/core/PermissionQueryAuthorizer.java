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

import java.util.List;
import java.util.Objects;

/**
 * Authorizes view methods against the permissions the application itself records.
 * <p>
 * Shares {@link AbstractPermissionAuthorizer#decide} with {@link PermissionCommandAuthorizer}, so both sides
 * fail closed identically by construction rather than by agreement.
 */
@ThreadSafe
public final class PermissionQueryAuthorizer extends AbstractPermissionAuthorizer implements QueryAuthorizer {

    private final RolesSupplier rolesSupplier;

    /**
     * Constructor for an application whose query side has no bypass roles to consider.
     *
     * @param lookup       Answers what the caller holds.
     * @param bypassPolicy Roles that skip the check entirely.
     */
    public PermissionQueryAuthorizer(final PermissionLookup lookup, final PermissionBypassPolicy bypassPolicy) {
        this(lookup, bypassPolicy, context -> List.of());
    }

    /**
     * Constructor with a supplier for the caller's roles.
     * <p>
     * Unlike the command side, nothing hands the query side a role list - the dispatcher that does it for
     * commands has no query equivalent. So the roles are fetched here, from wherever the runtime keeps them,
     * and the bypass works the same on both sides.
     *
     * @param lookup        Answers what the caller holds.
     * @param bypassPolicy  Roles that skip the check entirely.
     * @param rolesSupplier Returns the caller's roles for a given context.
     */
    public PermissionQueryAuthorizer(final PermissionLookup lookup, final PermissionBypassPolicy bypassPolicy,
                                     final RolesSupplier rolesSupplier) {
        super(lookup, bypassPolicy);
        this.rolesSupplier = Objects.requireNonNull(rolesSupplier, "rolesSupplier==null");
    }

    @Override
    public Result authorized(final String permissionId, final ExecutionContext context) {
        Objects.requireNonNull(permissionId, "permissionId==null");
        if (context == null) {
            return new Result(false, permissionId, null);
        }
        final List<SimpleRole> roles = rolesSupplier.rolesOf(context);
        final boolean allowed = decide(context, roles == null ? List.of() : roles, permissionId);
        return new Result(allowed, permissionId, heldBy(context));
    }

    /**
     * Returns the roles the caller has.
     */
    @FunctionalInterface
    public interface RolesSupplier {

        /**
         * Returns the roles for the given caller.
         *
         * @param context Who is calling.
         * @return Roles, never {@literal null}.
         */
        List<SimpleRole> rolesOf(ExecutionContext context);

    }

}
