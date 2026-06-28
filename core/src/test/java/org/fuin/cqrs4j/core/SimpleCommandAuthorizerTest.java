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

import org.fuin.ddd4j.core.SecurityRole;
import org.fuin.ddd4j.core.SimpleRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SimpleCommandAuthorizer}.
 */
public class SimpleCommandAuthorizerTest {

    @Test
    public void testDeniedWhenCommandNotConfigured() {
        final Map<Class<? extends Command>, List<SecurityRole>> map = Map.of();
        final SimpleCommandAuthorizer testee = new SimpleCommandAuthorizer(cmdClass -> Optional.empty());
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(new SimpleRole("admin")));
        assertThat(result.success()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Authorization for command MyCmd failed - " +
                "Allowed roles: NONE / User's roles: [admin]");
    }

    @Test
    public void testAllowedWhenNoRolesConfigured() {
        final SimpleCommandAuthorizer testee = new SimpleCommandAuthorizer(cmdClass -> Optional.of(List.of()));
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of());
        assertThat(result.success()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Authorization for command MyCmd successfully verified - " +
                "Allowed roles: [] / User's roles: []");
    }

    @Test
    public void testAllowedWhenUserHasMatchingRole() {
        final SimpleCommandAuthorizer testee = new SimpleCommandAuthorizer(cmdClass -> Optional.of(List.of(new SimpleRole("admin"))));
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(new SimpleRole("admin")));
        assertThat(result.success()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Authorization for command MyCmd successfully verified - " +
                "Allowed roles: [admin] / User's roles: [admin]");
    }

    @Test
    public void testDeniedWhenUserRoleDoesNotMatch() {
        final SimpleCommandAuthorizer testee = new SimpleCommandAuthorizer(cmdClass -> Optional.of(List.of(new SimpleRole("admin"))));
        final CommandAuthorizer.Result result = testee.authorized(new MyCmd(), List.of(new SimpleRole("user")));
        assertThat(result.success()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Authorization for command MyCmd failed - " +
                "Allowed roles: [admin] / User's roles: [user]");
    }

}
