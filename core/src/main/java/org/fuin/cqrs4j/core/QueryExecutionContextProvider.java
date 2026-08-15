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

/**
 * Provides the caller of the current query request.
 * <p>
 * The query-side counterpart of the command side's execution context provider. The command side gets its
 * context handed to it by the dispatcher on every request; queries have no dispatcher - there is a generated
 * controller method per view method and no choke point - so a view method's authorization check asks this
 * provider instead.
 * <p>
 * <b>It lives here, in the runtime-neutral module, rather than beside a particular web framework's
 * integration.</b> The command side's equivalent is referenced only by a hand-written endpoint, one per
 * runtime, so it can afford to sit in that runtime's module. This one is referenced by <em>generated</em>
 * code, and the same generator emits controllers for more than one runtime - so a home in any single
 * runtime's module would leave the others unable to compile what was generated for them. Implementations
 * belong in the runtime modules; the type they implement does not.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface QueryExecutionContextProvider {

    /**
     * Returns the context of the current request.
     *
     * @return Tenant and user of the caller.
     */
    QueryExecutionContext current();

    /**
     * Returns the roles of the current caller.
     * <p>
     * Needed because a coarse role can bypass the permission check, and unlike on the command side nothing
     * hands the roles to the authorizer.
     *
     * @return Roles, empty if the request is not authenticated.
     */
    List<SimpleRole> currentUserRoles();

}
