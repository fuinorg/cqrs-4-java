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
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link QueryAuthorization}.
 */
public class QueryAuthorizationTest {

    private static final String METHOD = "PersonView.listPersons";

    @Test
    public void testPassesWhenAuthorized() {
        final QueryAuthorizer authorizer = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of(METHOD)), PermissionBypassPolicy.none());
        assertThatCode(() -> QueryAuthorization.require(authorizer, METHOD, new TestExecutionContext()))
                .doesNotThrowAnyException();
    }

    @Test
    public void testThrowsWhenNotAuthorized() {
        final QueryAuthorizer authorizer = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of()), PermissionBypassPolicy.none());
        assertThatThrownBy(() -> QueryAuthorization.require(authorizer, METHOD, new TestExecutionContext()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void testMissingAuthorizerDeniesRatherThanSkippingTheCheck() {
        // A deployment that failed to wire an authorizer must refuse, not serve every read unchecked.
        assertThatThrownBy(() -> QueryAuthorization.require(null, METHOD, new TestExecutionContext()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void testMissingContextDenies() {
        final QueryAuthorizer authorizer = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of(METHOD)), PermissionBypassPolicy.none());
        assertThatThrownBy(() -> QueryAuthorization.require(authorizer, METHOD, null))
                .isInstanceOf(UnauthorizedException.class);
    }

}
