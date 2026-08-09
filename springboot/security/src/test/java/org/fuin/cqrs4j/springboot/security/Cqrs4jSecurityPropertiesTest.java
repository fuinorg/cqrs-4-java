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

package org.fuin.cqrs4j.springboot.security;

import org.fuin.cqrs4j.springboot.security.Cqrs4jSecurityProperties.Rule;
import org.fuin.cqrs4j.springboot.security.Cqrs4jSecurityProperties.Tenants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guards on the configuration model.
 * <p>
 * All three reject something that would otherwise <em>look</em> configured and enforce less than the
 * reader intended - which is the only interesting kind of mistake in a security setting.
 */
class Cqrs4jSecurityPropertiesTest {

    @Test
    void testTheDefaultsAreTheClosedOnes() {

        final Cqrs4jSecurityProperties testee = new Cqrs4jSecurityProperties(true, Tenants.SINGLE_REALM, null);

        assertThat(testee.permitActuator()).isTrue();
        assertThat(testee.tenants()).isEqualTo(Tenants.SINGLE_REALM);
        assertThat(testee.rules()).isEmpty();
        assertThat(testee.describe()).contains("every other request needs a valid bearer token");

    }

    /**
     * A rule with no roles is a rule that permits. This model has no permit, so rather than silently
     * degrading to "authenticated" - which reads like an enforced rule and is not one - it refuses.
     */
    @Test
    void testARuleMustRequireARole() {

        assertThatThrownBy(() -> new Rule(List.of("/cmd/**"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no 'has-any-role'");

    }

    @Test
    void testARuleMustNameAPath() {

        assertThatThrownBy(() -> new Rule(List.of(), List.of("admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no 'paths'");

    }

    /**
     * {@code hasAnyRole} prefixes the name itself, so a configured {@code ROLE_admin} becomes
     * {@code ROLE_ROLE_admin} and matches nobody - a rule that is on, looks right, and denies everyone.
     */
    @Test
    void testARoleMustNotCarryTheRolePrefix() {

        assertThatThrownBy(() -> new Rule(List.of("/cmd/**"), List.of("ROLE_admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without the 'ROLE_' prefix");

    }

    @Test
    void testTheDescriptionNamesEveryRule() {

        final Cqrs4jSecurityProperties testee = new Cqrs4jSecurityProperties(true, Tenants.SINGLE_REALM,
                List.of(new Rule(List.of("/cmd/**"), List.of("tenant-admin"))));

        assertThat(testee.describe())
                .contains("health and info are open")
                .contains("[/cmd/**] needs any of [tenant-admin]")
                .contains("every other request needs a valid bearer token");

    }

}
