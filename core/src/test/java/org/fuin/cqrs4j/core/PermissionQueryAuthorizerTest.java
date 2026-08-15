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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PermissionQueryAuthorizer}.
 */
public class PermissionQueryAuthorizerTest {

    private static final String METHOD = "PersonView.listPersons";

    @Test
    public void testAllowedWhenTheViewMethodsPermissionIsHeld() {
        final PermissionQueryAuthorizer testee = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of(METHOD)), PermissionBypassPolicy.none());
        assertThat(testee.authorized(METHOD, new TestExecutionContext()).success()).isTrue();
    }

    @Test
    public void testDeniedWhenTheViewMethodsPermissionIsNotHeld() {
        final PermissionQueryAuthorizer testee = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of("PersonView.findPerson")), PermissionBypassPolicy.none());
        assertThat(testee.authorized(METHOD, new TestExecutionContext()).success()).isFalse();
    }

    @Test
    public void testDeniedWithoutAContext() {
        final PermissionQueryAuthorizer testee = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of(METHOD)), PermissionBypassPolicy.none());
        assertThat(testee.authorized(METHOD, null).success()).isFalse();
    }

    @Test
    public void testBypassRoleAppliesOnTheQuerySideToo() {
        // The bypass has to work identically on both sides, or the administrator can write but not read.
        final PermissionQueryAuthorizer testee = new PermissionQueryAuthorizer(
                context -> Optional.empty(), new PermissionBypassPolicy(Set.of("ROLE_org-admin")),
                context -> List.of(new SimpleRole("ROLE_org-admin")));
        assertThat(testee.authorized(METHOD, new TestExecutionContext()).success()).isTrue();
    }

    @Test
    public void testMessageOnSuccess() {
        final PermissionQueryAuthorizer testee = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of(METHOD)), PermissionBypassPolicy.none());
        assertThat(testee.authorized(METHOD, new TestExecutionContext()).getMessage())
                .isEqualTo("Authorization for view method PersonView.listPersons successfully verified"
                        + " - Required permission: PersonView.listPersons"
                        + " / User's permissions: [PersonView.listPersons]");
    }

    @Test
    public void testMessageOnFailureWithUnknownPermissions() {
        final PermissionQueryAuthorizer testee = new PermissionQueryAuthorizer(
                context -> Optional.empty(), PermissionBypassPolicy.none());
        assertThat(testee.authorized(METHOD, new TestExecutionContext()).getMessage())
                .isEqualTo("Authorization for view method PersonView.listPersons failed"
                        + " - Required permission: PersonView.listPersons"
                        + " / User's permissions: []");
    }

    @Test
    public void testMessageSortsThePermissionsSoALogLineIsStable() {
        final PermissionQueryAuthorizer testee = new PermissionQueryAuthorizer(
                context -> Optional.of(Set.of("c", "a", "b")), PermissionBypassPolicy.none());
        assertThat(testee.authorized(METHOD, new TestExecutionContext()).getMessage())
                .endsWith("User's permissions: [a, b, c]");
    }

}
