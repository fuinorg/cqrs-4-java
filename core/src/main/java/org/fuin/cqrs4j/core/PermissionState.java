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
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the authorization projection has folded out of the event streams: who holds which permissions.
 * <p>
 * Two maps, because a permission is granted to a <em>holder</em> - an aggregate such as a person - while a
 * request arrives carrying a <em>subject id</em> from the identity provider. The events that grant a
 * permission and the event that binds a login to the holder are different events, and they may arrive in
 * either order, so the two facts are kept apart and joined on read.
 * <p>
 * A subject may map to more than one holder. That is not the common case today, but it is what allows a
 * second source - a team membership, say - to contribute permissions to the same caller without this class
 * changing: the lookup unions over every holder the subject is.
 * <p>
 * <b>Every key carries the tenant.</b> Not because anything reads more than one tenant today - the runner
 * that feeds this refuses to start when multitenancy is on - but because the moment something does, a map
 * keyed by subject id alone would union two tenants' grants for the same subject. That is a cross-tenant
 * privilege escalation rather than an outage, and it is the kind of thing that is far cheaper to make
 * impossible now than to notice later.
 * <p>
 * Writes come from a single projection thread; reads come from request threads. Both maps are concurrent and
 * every stored set is replaced rather than mutated, so a reader never observes a half-updated set.
 */
@ThreadSafe
public final class PermissionState {

    /** Tenant plus holder id (e.g. a person's aggregate id) to the permissions granted to it. */
    private final Map<Key, Set<String>> permissionsByHolder = new ConcurrentHashMap<>();

    /** Tenant plus subject id from the identity provider to the holders it is. */
    private final Map<Key, Set<String>> holdersBySubject = new ConcurrentHashMap<>();

    /**
     * Adds permissions to a holder. Granting one that is already held changes nothing - the fold is a union.
     *
     * @param tenantId      Tenant the holder belongs to.
     * @param holderId      Holder the permissions are granted to.
     * @param permissionIds Permission ids to add.
     */
    public void grant(final TenantId tenantId, final String holderId, final Collection<String> permissionIds) {
        Objects.requireNonNull(permissionIds, "permissionIds==null");
        permissionsByHolder.compute(new Key(tenantId, holderId), (key, current) -> {
            final Set<String> next = current == null ? new LinkedHashSet<>() : new LinkedHashSet<>(current);
            next.addAll(permissionIds);
            return Set.copyOf(next);
        });
    }

    /**
     * Removes permissions from a holder. Revoking one that is not held changes nothing.
     *
     * @param tenantId      Tenant the holder belongs to.
     * @param holderId      Holder the permissions are taken from.
     * @param permissionIds Permission ids to remove.
     */
    public void revoke(final TenantId tenantId, final String holderId, final Collection<String> permissionIds) {
        Objects.requireNonNull(permissionIds, "permissionIds==null");
        permissionsByHolder.computeIfPresent(new Key(tenantId, holderId), (key, current) -> {
            final Set<String> next = new LinkedHashSet<>(current);
            next.removeAll(permissionIds);
            return Set.copyOf(next);
        });
    }

    /**
     * Binds a login to a holder, so that permissions granted to the holder answer for that subject.
     * <p>
     * Ordering does not matter: permissions granted before the login existed become effective the moment the
     * binding arrives.
     *
     * @param tenantId  Tenant both belong to.
     * @param subjectId Subject id the identity provider issued.
     * @param holderId  Holder the login belongs to.
     */
    public void bindSubject(final TenantId tenantId, final String subjectId, final String holderId) {
        Objects.requireNonNull(holderId, "holderId==null");
        holdersBySubject.compute(new Key(tenantId, subjectId), (key, current) -> {
            final Set<String> next = current == null ? new LinkedHashSet<>() : new LinkedHashSet<>(current);
            next.add(holderId);
            return Set.copyOf(next);
        });
    }

    /**
     * Removes a login binding. The holder and its permissions stay - only this subject stops resolving to it.
     *
     * @param tenantId  Tenant the subject belongs to.
     * @param subjectId Subject id that no longer belongs to anybody.
     */
    public void unbindSubject(final TenantId tenantId, final String subjectId) {
        holdersBySubject.remove(new Key(tenantId, subjectId));
    }

    /**
     * Removes a holder along with its permissions and every login bound to it.
     *
     * @param tenantId Tenant the holder belongs to.
     * @param holderId Holder that no longer exists.
     */
    public void removeHolder(final TenantId tenantId, final String holderId) {
        Objects.requireNonNull(holderId, "holderId==null");
        permissionsByHolder.remove(new Key(tenantId, holderId));
        // compute() rather than an entrySet iterator: the sets are immutable copies, so an in-place
        // setValue is not available, and returning null from compute is how an entry is dropped. Only this
        // tenant's subjects are touched.
        for (final Key key : Set.copyOf(holdersBySubject.keySet())) {
            if (!key.tenant().equals(tenantId)) {
                continue;
            }
            holdersBySubject.computeIfPresent(key, (k, current) -> {
                if (!current.contains(holderId)) {
                    return current;
                }
                final Set<String> remaining = new LinkedHashSet<>(current);
                remaining.remove(holderId);
                return remaining.isEmpty() ? null : Set.copyOf(remaining);
            });
        }
    }

    /**
     * Returns everything the given subject holds in the given tenant, unioned over every holder it is.
     * <p>
     * An <b>empty set</b> means the subject is known and holds nothing. A subject that is not bound to any
     * holder also yields an empty set - the caller cannot distinguish the two, and does not need to: both
     * mean "may do nothing". The "not ready yet" case is expressed by
     * {@link PermissionLookup#permissionsOf(ExecutionContext)} returning an empty {@link java.util.Optional},
     * not here.
     *
     * @param tenantId  Tenant to look in.
     * @param subjectId Subject id to look up.
     * @return Permission ids held, never {@literal null}.
     */
    public Set<String> permissionsOfSubject(final TenantId tenantId, final String subjectId) {
        final Set<String> holders = holdersBySubject.get(new Key(tenantId, subjectId));
        if (holders == null || holders.isEmpty()) {
            return Set.of();
        }
        final Set<String> union = new LinkedHashSet<>();
        for (final String holderId : holders) {
            final Set<String> held = permissionsByHolder.get(new Key(tenantId, holderId));
            if (held != null) {
                union.addAll(held);
            }
        }
        return Set.copyOf(union);
    }

    /**
     * Returns the holders a subject is, within one tenant.
     * <p>
     * This is the subject-to-holder index the command side would otherwise not have: the read model's
     * "find by subject" lives on the query side only.
     *
     * @param tenantId  Tenant to look in.
     * @param subjectId Subject id to look up.
     * @return Holder ids, empty if the subject is bound to nothing.
     */
    public Set<String> holdersOfSubject(final TenantId tenantId, final String subjectId) {
        final Set<String> holders = holdersBySubject.get(new Key(tenantId, subjectId));
        return holders == null ? Set.of() : holders;
    }

    /**
     * Discards everything. Used when a replay starts from the beginning of the stream.
     */
    public void clear() {
        permissionsByHolder.clear();
        holdersBySubject.clear();
    }

    /**
     * Tenant-scoped map key.
     *
     * @param tenant Tenant the entry belongs to.
     * @param id     Holder or subject id, depending on the map.
     */
    private record Key(TenantId tenant, String id) {

        private Key {
            Objects.requireNonNull(tenant, "tenant==null");
            Objects.requireNonNull(id, "id==null");
        }

    }

}
