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

import org.fuin.cqrs4j.core.TenantRepository;
import org.fuin.ddd4j.core.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TenantRoutingDataSource}.
 */
public class TenantRoutingDataSourceTest {

    @Test
    public void testDefaultLookupKeyWhenNoTenant() {
        final TenantRepository tenantRepository = Stream::empty;
        final TenantContext context = Optional::empty;

        final TenantRoutingDataSource testee = new TenantRoutingDataSource(
                "jdbc:hsqldb:mem:main;DB_CLOSE_DELAY=-1", "sa", "", "org.hsqldb.jdbc.JDBCDriver", "main",
                Optional.of(context), tenantRepository);

        // No tenant in the context -> the default schema is used as the routing key
        assertThat(testee.determineCurrentLookupKey()).isEqualTo("main");
    }

}
