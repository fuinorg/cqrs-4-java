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
package org.fuin.cqrs4j.springboot.query.core;

import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import javax.sql.DataSource;

/**
 * A {@link DataSource} that is aware of tenants being added or removed at runtime and adjusts its
 * managed connections accordingly.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface TenantDataSource extends DataSource {

    /**
     * Reacts to a newly added tenant by registering a data source for it.
     *
     * @param event Event carrying the added tenant.
     */
    @EventListener
    @Async
    void handleEvent(TenantAddedEvent event);

    /**
     * Reacts to a removed tenant by discarding the data source for it.
     *
     * @param event Event carrying the removed tenant.
     */
    @EventListener
    @Async
    void handleEvent(TenantRemovedEvent event);

}
