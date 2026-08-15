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

import java.util.Optional;
import java.util.Set;

/**
 * Answers what the calling user is allowed to do.
 * <p>
 * One port, two sides. The command side answers it from a projection it maintains itself; the query side
 * answers it from the read model a view already maintains. Nothing here assumes either, and in particular
 * nothing here assumes a read model is available locally - that is what lets the two sides be deployed
 * apart.
 * <p>
 * An implementation must <b>never load an aggregate</b> to answer this. Reading the write model is a command
 * handler's job, and an authorizer that did it would only work for as long as the aggregate in question
 * happens to be hosted in the same process.
 * <p>
 * An implementation must also scope its answer to {@link ExecutionContext#getTenantId()}. Both the caller
 * and the data are tenant-bound, and a lookup that keyed on the subject alone would answer one tenant's
 * question with another tenant's grants.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface PermissionLookup {

    /**
     * Returns the permission ids the caller holds.
     * <p>
     * The distinction between the two "nothing" cases is load bearing:
     * <ul>
     * <li>An <b>empty {@link Optional}</b> means the answer is <em>unknown</em> - the caller could not be
     * identified, or the lookup is not ready (still catching up, or its data has gone stale). The authorizer
     * denies.</li>
     * <li>A <b>present but empty {@link Set}</b> means the answer is known and the caller holds nothing. The
     * authorizer also denies, but for a reason that will not change by waiting.</li>
     * </ul>
     * Collapsing the two would turn "not ready yet" into "definitely allowed nothing", which reads the same
     * from the outside but hides an outage behind a permissions problem.
     *
     * @param context Who is calling.
     * @return Permission ids held, or empty if the answer is not known.
     */
    Optional<Set<String>> permissionsOf(ExecutionContext context);

}
