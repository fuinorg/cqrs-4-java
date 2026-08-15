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
import org.fuin.objects4j.common.Immutable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Roles whose holders skip the permission check entirely.
 * <p>
 * This exists because an application has to be administrable before anybody holds a permission in it. The
 * account that provisions the first users is created in the identity provider, not by the application, so it
 * has no record in the application's own data and the lookup can never answer for it. A static role carried
 * in the token is the only thing available at that moment.
 * <p>
 * The mechanism is here; <b>the contents are the application's</b>. Keep the set as small as the application
 * can stand, hold it in one place, and pin it with a test - a bypass that grows a role at a time stops being
 * a bootstrap and becomes the way the system is used. {@link AbstractPermissionAuthorizer} logs every
 * decision this policy waves through, so that its use is at least visible.
 * <p>
 * The role names must be spelled the way they arrive at the authorizer, not the way they are spelled in the
 * identity provider. A converter that turns a realm role into an authority typically prefixes it, and the
 * mismatch is silent: everything simply denies.
 *
 * @param allowAllRoles Roles that bypass every permission check.
 */
@Immutable
public record PermissionBypassPolicy(Set<String> allowAllRoles) {

    /**
     * Compact constructor taking a defensive copy.
     */
    public PermissionBypassPolicy {
        Objects.requireNonNull(allowAllRoles, "allowAllRoles==null");
        allowAllRoles = Set.copyOf(allowAllRoles);
    }

    /**
     * A policy that lets nothing through.
     *
     * @return Empty policy.
     */
    public static PermissionBypassPolicy none() {
        return new PermissionBypassPolicy(Set.of());
    }

    /**
     * Determines whether any of the caller's roles bypasses the permission check.
     *
     * @param userRoles Roles the caller has, as they arrive at the authorizer.
     * @return TRUE if the check should be skipped.
     */
    public boolean bypasses(final List<SimpleRole> userRoles) {
        if (userRoles == null || allowAllRoles.isEmpty()) {
            return false;
        }
        for (final SimpleRole role : userRoles) {
            if (role != null && allowAllRoles.contains(role.name())) {
                return true;
            }
        }
        return false;
    }

}
