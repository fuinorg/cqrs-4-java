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
 * Tests for {@link PermissionCommandAuthorizer}.
 */
public class PermissionCommandAuthorizerTest {

    private static final PermissionBypassPolicy NO_BYPASS = PermissionBypassPolicy.none();

    @Test
    public void testAllowedWhenTheCommandsPermissionIsHeld() {
        // The default mapping is "the command's simple name is its permission id".
        final PermissionCommandAuthorizer testee = new PermissionCommandAuthorizer(
                context -> Optional.of(Set.of("MyCmd")), NO_BYPASS);
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(), new TestExecutionContext());
        assertThat(result.success()).isTrue();
    }

    @Test
    public void testDeniedWhenTheCommandsPermissionIsNotHeld() {
        final PermissionCommandAuthorizer testee = new PermissionCommandAuthorizer(
                context -> Optional.of(Set.of("SomethingElse")), NO_BYPASS);
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(), new TestExecutionContext());
        assertThat(result.success()).isFalse();
    }

    @Test
    public void testDeniedWithoutAContext() {
        // The two-argument overload exists only because the interface declares it. Without a context there is
        // nobody to look up, and guessing is not an option.
        final PermissionCommandAuthorizer testee = new PermissionCommandAuthorizer(
                context -> Optional.of(Set.of("MyCmd")), NO_BYPASS);
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of());
        assertThat(result.success()).isFalse();
    }

    @Test
    public void testDeniedWhenTheCommandHasNoPermissionId() {
        final PermissionCommandAuthorizer testee = new PermissionCommandAuthorizer(
                context -> Optional.of(Set.of("MyCmd")), NO_BYPASS, cmdClass -> null);
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(), new TestExecutionContext());
        assertThat(result.success()).isFalse();
    }

    @Test
    public void testBypassRoleWinsWithoutAnyLookupAnswer() {
        final PermissionCommandAuthorizer testee = new PermissionCommandAuthorizer(
                context -> Optional.empty(), new PermissionBypassPolicy(Set.of("ROLE_org-admin")));
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(),
                List.of(new SimpleRole("ROLE_org-admin")), new TestExecutionContext());
        assertThat(result.success()).isTrue();
    }

    @Test
    public void testMessageNamesTheRequiredPermission() {
        final PermissionCommandAuthorizer testee = new PermissionCommandAuthorizer(
                context -> Optional.of(Set.of()), NO_BYPASS);
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(), new TestExecutionContext());
        assertThat(result.getMessage()).contains("MyCmd");
    }

    @Test
    public void testCustomPermissionIdMapping() {
        final PermissionCommandAuthorizer testee = new PermissionCommandAuthorizer(
                context -> Optional.of(Set.of("cmd:my")), NO_BYPASS, cmdClass -> "cmd:my");
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(), new TestExecutionContext());
        assertThat(result.success()).isTrue();
    }

}
