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

import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Decides if a user has the rights to call a view method.
 * <p>
 * The query-side counterpart of {@link CommandAuthorizer}, and the second of the two enforcement points an
 * application needs. Checking commands and leaving queries open is the common half-implementation; it fails
 * in the direction that leaks data.
 * <p>
 * The operation is identified by its <b>catalogue permission id</b> - one string, e.g.
 * {@code "PersonView.listPersons"} - rather than by a view name and a method name separately. There is then
 * no assembly rule that a caller could get subtly wrong, and the generated call site can pass the same
 * literal the catalogue publishes.
 * <p>
 * Note what this does <b>not</b> answer: which rows come back. "May call this method" and "may see this row"
 * are different axes, and an application whose read models are scoped needs a predicate in the query as
 * well. This interface is only the first of the two.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface QueryAuthorizer {

    /**
     * Determines if a user is authorized to call the given view method.
     *
     * @param permissionId Catalogue id of the view method, e.g. {@code "PersonView.listPersons"}.
     * @param context      Who is calling.
     * @return Result of the verification.
     */
    Result authorized(String permissionId, ExecutionContext context);

    /**
     * Result of the authorization check.
     *
     * @param success         If the user is authorized to call the view method {@literal true}.
     * @param permissionId    Catalogue id that was checked.
     * @param heldPermissions Permission ids the caller holds, or {@literal null} if that is not known.
     */
    record Result(boolean success, String permissionId, @Nullable Set<String> heldPermissions) {

        /**
         * Returns a message for the result.
         *
         * @return Result information.
         */
        public String getMessage() {
            final String result;
            if (success) {
                result = "Authorization for view method " + permissionId + " successfully verified";
            } else {
                result = "Authorization for view method " + permissionId + " failed";
            }
            final String heldStr;
            if (heldPermissions == null) {
                heldStr = "UNKNOWN";
            } else {
                // Sorted, because a set's iteration order is not something a log line should vary by.
                heldStr = new TreeSet<>(heldPermissions).stream()
                        .collect(Collectors.joining(", ", "[", "]"));
            }
            return result + " - Required permission: " + permissionId + " / User's permissions: " + heldStr;
        }

    }

}
