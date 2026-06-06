package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.TenantId;

import java.util.stream.Stream;

/**
 * Supplies the tenant identifiers know at the moment of the call.
 * <p>
 * CAUTION: Implementations are required to be thread-safe!
 * </p>
 */
public interface TenantIdsSupplier {

    /**
     * Returns the unique tenant identifiers known.
     *
     * @return Stream of tenant IDs.
     */
    Stream<TenantId> getTenantIds();

}
