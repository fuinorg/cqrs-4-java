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

import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PermissionState}.
 */
public class PermissionStateTest {

    private static final String HOLDER = "person-1";

    private static final String SUBJECT = "subject-1";

    private static final TenantId T = new TenantId("acme");

    @Test
    public void testGrantThenBind() {
        final PermissionState testee = new PermissionState();
        testee.grant(T, HOLDER, List.of("A", "B"));
        testee.bindSubject(T, SUBJECT, HOLDER);
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    public void testBindThenGrant() {
        // The order the two facts arrive in is not controlled by anything: a login may be granted before or
        // after the permissions. Both orders must end in the same place, or the answer depends on the shape
        // of the stream rather than on what happened.
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A", "B"));
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    public void testGrantIsAUnionAndIsIdempotent() {
        // The projection replays from the start on every boot, so applying the same event twice must not
        // change anything.
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A"));
        testee.grant(T, HOLDER, List.of("A", "B"));
        testee.grant(T, HOLDER, List.of("A"));
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    public void testRevoke() {
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A", "B"));
        testee.revoke(T, HOLDER, List.of("A"));
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactly("B");
    }

    @Test
    public void testRevokingSomethingNotHeldChangesNothing() {
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A"));
        testee.revoke(T, HOLDER, List.of("Z"));
        testee.revoke(T, "nobody", List.of("A"));
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactly("A");
    }

    @Test
    public void testUnknownSubjectHoldsNothing() {
        final PermissionState testee = new PermissionState();
        testee.grant(T, HOLDER, List.of("A"));
        assertThat(testee.permissionsOfSubject(T, "nobody")).isEmpty();
    }

    @Test
    public void testUnbindSubjectKeepsTheHolder() {
        // Taking a login away does not delete the person, so re-granting a login restores the permissions.
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A"));
        testee.unbindSubject(T, SUBJECT);
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).isEmpty();
        testee.bindSubject(T, SUBJECT, HOLDER);
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactly("A");
    }

    @Test
    public void testRemoveHolderDropsPermissionsAndBinding() {
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A"));
        testee.removeHolder(T, HOLDER);
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).isEmpty();
        assertThat(testee.holdersOfSubject(T, SUBJECT)).isEmpty();
    }

    @Test
    public void testUnionOverSeveralHolders() {
        // What lets a second source contribute to the same caller without this class changing.
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.bindSubject(T, SUBJECT, "team-7");
        testee.grant(T, HOLDER, List.of("A"));
        testee.grant(T, "team-7", List.of("B"));
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactlyInAnyOrder("A", "B");

        testee.removeHolder(T, "team-7");
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactly("A");
        assertThat(testee.holdersOfSubject(T, SUBJECT)).containsExactly(HOLDER);
    }

    @Test
    public void testHoldersOfSubjectIsTheSubjectToHolderIndex() {
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        assertThat(testee.holdersOfSubject(T, SUBJECT)).containsExactly(HOLDER);
        assertThat(testee.holdersOfSubject(T, "nobody")).isEmpty();
    }

    @Test
    public void testTenantsAreIsolatedForTheSameSubjectId() {
        // The reason every key carries the tenant. Nothing reads two tenants today, but the moment something
        // does, a map keyed by subject alone would hand tenant A's caller tenant B's grants - an escalation
        // across the strongest boundary in the system, and one that no test would notice by accident.
        final TenantId other = new TenantId("other");
        final PermissionState testee = new PermissionState();

        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A"));
        testee.bindSubject(other, SUBJECT, HOLDER);
        testee.grant(other, HOLDER, List.of("B"));

        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactly("A");
        assertThat(testee.permissionsOfSubject(other, SUBJECT)).containsExactly("B");
    }

    @Test
    public void testRemovingAHolderLeavesTheOtherTenantAlone() {
        final TenantId other = new TenantId("other");
        final PermissionState testee = new PermissionState();

        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, List.of("A"));
        testee.bindSubject(other, SUBJECT, HOLDER);
        testee.grant(other, HOLDER, List.of("B"));

        testee.removeHolder(T, HOLDER);

        assertThat(testee.permissionsOfSubject(T, SUBJECT)).isEmpty();
        assertThat(testee.holdersOfSubject(T, SUBJECT)).isEmpty();
        assertThat(testee.permissionsOfSubject(other, SUBJECT)).containsExactly("B");
    }

    @Test
    public void testWhatOneHolderWasGranted() {
        // The lookup for an application whose holders are not interchangeable: a subject that is several
        // holders but acts as one of them at a time, with the caller saying which.
        final String otherHolder = "person-2";
        final PermissionState testee = new PermissionState();

        testee.grant(T, HOLDER, List.of("A"));
        testee.grant(T, otherHolder, List.of("B"));
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.bindSubject(T, SUBJECT, otherHolder);

        assertThat(testee.permissionsOfHolder(T, HOLDER)).containsExactly("A");
        assertThat(testee.permissionsOfHolder(T, otherHolder)).containsExactly("B");
        // While the subject-keyed lookup still unions them, which is the answer this one exists beside.
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    public void testAHolderNobodyGrantedAnythingHoldsNothing() {
        assertThat(new PermissionState().permissionsOfHolder(T, "never-seen")).isEmpty();
    }

    @Test
    public void testAHoldersGrantsAreIsolatedPerTenant() {
        // The same reason every other key carries the tenant: asking for a holder by name in the wrong
        // tenant must not answer with the right one's grants.
        final PermissionState testee = new PermissionState();
        testee.grant(T, HOLDER, List.of("A"));

        assertThat(testee.permissionsOfHolder(new TenantId("other"), HOLDER)).isEmpty();
    }

    @Test
    public void testClear() {
        final PermissionState testee = new PermissionState();
        testee.bindSubject(T, SUBJECT, HOLDER);
        testee.grant(T, HOLDER, Set.of("A"));
        testee.clear();
        assertThat(testee.permissionsOfSubject(T, SUBJECT)).isEmpty();
        assertThat(testee.holdersOfSubject(T, SUBJECT)).isEmpty();
    }

}
