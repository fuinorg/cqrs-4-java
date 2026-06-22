package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.stream.Stream;

/**
 * Supplies the tenant identifiers know at the moment of the call.
 * <p>
 * All implementations are expected to be thread safe.
 * </p>
 */
@ThreadSafe
public interface TenantIdsSupplier {

    /**
     * Returns the unique tenant identifiers known.
     *
     * @return Stream of tenant IDs.
     */
    Stream<TenantId> getTenantIds();

}
