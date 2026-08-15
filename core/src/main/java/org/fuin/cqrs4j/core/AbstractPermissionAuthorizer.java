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
import org.fuin.ddd4j.core.User;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The one authorization decision, shared by the command side and the query side.
 * <p>
 * Commands and queries are checked in different places - one dispatcher for all commands, one generated
 * check per view method - but they must answer the same way, and in particular they must <b>fail closed the
 * same way</b>. "Commands checked, queries open" is the usual half-implementation, and it fails silently in
 * exactly the direction that leaks data. Putting the decision in one inherited method is cheaper than
 * pinning the same behaviour twice with a test.
 * <p>
 * The order of the checks matters. The bypass is evaluated <em>before</em> the lookup, so an account that
 * holds a bypass role never has to appear in the application's own data at all - which is the point of it,
 * since the account that bootstraps an installation is created in the identity provider and has no record
 * here.
 */
@ThreadSafe
public abstract class AbstractPermissionAuthorizer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractPermissionAuthorizer.class);

    private final PermissionLookup lookup;

    private final PermissionBypassPolicy bypassPolicy;

    /**
     * Constructor with the lookup and the bypass policy.
     *
     * @param lookup       Answers what the caller holds.
     * @param bypassPolicy Roles that skip the check entirely.
     */
    protected AbstractPermissionAuthorizer(final PermissionLookup lookup, final PermissionBypassPolicy bypassPolicy) {
        this.lookup = Objects.requireNonNull(lookup, "lookup==null");
        this.bypassPolicy = Objects.requireNonNull(bypassPolicy, "bypassPolicy==null");
    }

    /**
     * Decides whether the caller may perform the operation.
     *
     * @param context      Who is calling.
     * @param userRoles    Roles the caller has, as they arrive here.
     * @param permissionId Catalogue id of the operation, or {@literal null} if it has no entry.
     * @return TRUE if the operation is allowed.
     */
    protected final boolean decide(final ExecutionContext context, final List<SimpleRole> userRoles,
                                   @Nullable final String permissionId) {

        if (permissionId == null) {
            // An operation with no catalogue entry is denied, the same way a command with no role mapping is.
            // The alternative - allowing what nobody classified - is how an unchecked operation ships.
            LOG.warn("Denied: the operation has no permission id, so nothing can be checked against it");
            return false;
        }

        if (bypassPolicy.bypasses(userRoles)) {
            // Logged on every use, deliberately. A bypass nobody can see being used is the failure mode.
            LOG.info("Bypass: '{}' allowed for subject '{}' by a bypass role", permissionId, subjectOf(context));
            return true;
        }

        final Optional<Set<String>> held = lookup.permissionsOf(context);
        if (held.isEmpty()) {
            // Unknown caller, or the lookup is not ready. Either way there is no basis for allowing anything.
            LOG.debug("Denied: '{}' - no permissions known for subject '{}' (unidentified caller, or the "
                    + "lookup has not caught up)", permissionId, subjectOf(context));
            return false;
        }

        if (held.get().contains(permissionId)) {
            return true;
        }

        LOG.debug("Denied: '{}' is not held by subject '{}'", permissionId, subjectOf(context));
        return false;
    }

    /**
     * Returns what the caller holds, for building a result message. Never used to decide anything.
     *
     * @param context Who is calling.
     * @return Permission ids held, empty if not known.
     */
    protected final Set<String> heldBy(final ExecutionContext context) {
        return lookup.permissionsOf(context).orElseGet(Set::of);
    }

    private static String subjectOf(@Nullable final ExecutionContext context) {
        if (context == null) {
            return "?";
        }
        final User user = context.getUser();
        return user == null ? "?" : user.getUserId();
    }

}
