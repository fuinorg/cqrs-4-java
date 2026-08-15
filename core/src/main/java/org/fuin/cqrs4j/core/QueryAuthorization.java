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

import org.fuin.ddd4j.core.UnauthorizedException;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single line a generated view controller calls before serving a request.
 * <p>
 * Commands funnel through one dispatcher, so one call site covers all of them. Queries do not: there is a
 * controller method per view method and therefore no choke point, which is why this check is generated into
 * every one of them rather than added by hand. A view method somebody forgets to guard is an unchecked read,
 * and it looks exactly like a working one.
 * <p>
 * Kept to a static helper so the generated code is a single unambiguous line with nothing to get wrong.
 */
@ThreadSafe
public final class QueryAuthorization {

    private static final Logger LOG = LoggerFactory.getLogger(QueryAuthorization.class);

    private QueryAuthorization() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of a utility class");
    }

    /**
     * Verifies that the caller may call the view method, and throws if not.
     * <p>
     * A missing authorizer is treated as a denial, not as an absence of a check: a deployment that failed to
     * wire one must not thereby serve every read to everybody.
     *
     * @param authorizer   Decides the question. A {@literal null} denies.
     * @param permissionId Catalogue id of the view method, e.g. {@code "PersonView.listPersons"}.
     * @param context      Who is calling. A {@literal null} denies.
     * @throws UnauthorizedException The caller may not call this view method.
     */
    public static void require(final QueryAuthorizer authorizer, final String permissionId,
                               final ExecutionContext context) {
        if (authorizer == null) {
            LOG.error("Denied '{}': no QueryAuthorizer is configured. This is a wiring error - the query side "
                    + "is refusing every request rather than serving them unchecked.", permissionId);
            throw new UnauthorizedException();
        }
        final QueryAuthorizer.Result result = authorizer.authorized(permissionId, context);
        if (!result.success()) {
            // The exception carries no message, so the reason has to be logged here or it is lost.
            LOG.debug("{}", result.getMessage());
            throw new UnauthorizedException();
        }
    }

}
