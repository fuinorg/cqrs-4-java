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
 * Tests for {@link AbstractPermissionAuthorizer}.
 * <p>
 * This is the one place the fail-closed behaviour is defined, and both the command-side and the query-side
 * authorizer inherit it. Testing it once here is what makes "commands checked, queries open" impossible by
 * construction rather than by agreement, so every row of the decision table gets a case - including the ones
 * that deny, which are the ones worth having.
 */
public class AbstractPermissionAuthorizerTest {

    private static final String BYPASS_ROLE = "ROLE_org-admin";

    private static final String PERMISSION = "CreatePersonCommand";

    @Test
    public void testDeniedWhenOperationHasNoPermissionId() {
        // An operation nobody classified is denied. Allowing it is how an unchecked operation ships.
        final Testee testee = testee(Optional.of(Set.of(PERMISSION)));
        assertThat(testee.call(List.of(), null)).isFalse();
    }

    @Test
    public void testAllowedWhenCallerHoldsABypassRole() {
        final Testee testee = testee(Optional.of(Set.of()));
        assertThat(testee.call(List.of(new SimpleRole(BYPASS_ROLE)), PERMISSION)).isTrue();
    }

    @Test
    public void testBypassIsCheckedBeforeTheLookup() {
        // The account that bootstraps an installation exists only in the identity provider, so the lookup can
        // never answer for it. If the bypass were evaluated after the lookup, that account could do nothing
        // and the application would be unadministrable.
        final Testee testee = testee(Optional.empty());
        assertThat(testee.call(List.of(new SimpleRole(BYPASS_ROLE)), PERMISSION)).isTrue();
        assertThat(testee.lookupCalls()).isZero();
    }

    @Test
    public void testDeniedWhenLookupAnswerIsUnknown() {
        // Empty Optional means "not ready or unidentified", which is not a basis for allowing anything.
        final Testee testee = testee(Optional.empty());
        assertThat(testee.call(List.of(), PERMISSION)).isFalse();
    }

    @Test
    public void testDeniedWhenCallerIsKnownButHoldsNothing() {
        // A present-but-empty set is a different answer from an empty Optional, and both deny.
        final Testee testee = testee(Optional.of(Set.of()));
        assertThat(testee.call(List.of(), PERMISSION)).isFalse();
    }

    @Test
    public void testAllowedWhenCallerHoldsThePermission() {
        final Testee testee = testee(Optional.of(Set.of(PERMISSION, "RenamePersonCommand")));
        assertThat(testee.call(List.of(), PERMISSION)).isTrue();
    }

    @Test
    public void testDeniedWhenCallerHoldsADifferentPermission() {
        final Testee testee = testee(Optional.of(Set.of("RenamePersonCommand")));
        assertThat(testee.call(List.of(), PERMISSION)).isFalse();
    }

    @Test
    public void testWholeViewGroupIdIsNeverConsulted() {
        // A role may store "PersonView.*", but a check is always against the single operation. Expanding the
        // group is the lookup's job; matching the group here would let a handler test something coarser than
        // the operation it is guarding.
        final Testee testee = testee(Optional.of(Set.of("PersonView.*")));
        assertThat(testee.call(List.of(), "PersonView.listPersons")).isFalse();
    }

    @Test
    public void testRolesThatAreNotBypassRolesDoNotHelp() {
        final Testee testee = testee(Optional.of(Set.of()));
        assertThat(testee.call(List.of(new SimpleRole("ROLE_user")), PERMISSION)).isFalse();
    }

    private static Testee testee(final Optional<Set<String>> held) {
        return new Testee(new CountingLookup(held), new PermissionBypassPolicy(Set.of(BYPASS_ROLE)));
    }

    /**
     * Minimal concrete subclass exposing the protected decision.
     */
    private static final class Testee extends AbstractPermissionAuthorizer {

        private final CountingLookup lookup;

        private Testee(final CountingLookup lookup, final PermissionBypassPolicy policy) {
            super(lookup, policy);
            this.lookup = lookup;
        }

        private boolean call(final List<SimpleRole> roles, final String permissionId) {
            return decide(new TestExecutionContext(), roles, permissionId);
        }

        private int lookupCalls() {
            return lookup.calls;
        }

    }

    private static final class CountingLookup implements PermissionLookup {

        private final Optional<Set<String>> held;

        private int calls;

        private CountingLookup(final Optional<Set<String>> held) {
            this.held = held;
        }

        @Override
        public Optional<Set<String>> permissionsOf(final ExecutionContext context) {
            calls++;
            return held;
        }

    }

}
