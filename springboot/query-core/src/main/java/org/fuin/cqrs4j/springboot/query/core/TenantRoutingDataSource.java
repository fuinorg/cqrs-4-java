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

import org.fuin.ddd4j.core.TenantContext;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.cqrs4j.core.TenantRepository;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.scheduling.annotation.Async;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A datasource that switches the database connection based on a tenant in the context.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource implements TenantDataSource {

    private static final Logger LOG = LoggerFactory.getLogger(TenantRoutingDataSource.class);

    private final String jdbcUrl;

    private final String username;

    private final String password;

    private final String driverClassName;

    private final String defaultSchemaName;

    private final JdbcUrlReplacer replacer;

    private final TenantContext context;

    private final Map<Object, Object> dataSourceMap;

    /**
     * Constructor for a HSQL in-memory database.
     *
     * @param jdbcUrl           JDBC url like "jdbc:hsqldb:mem:main;DB_CLOSE_DELAY=-1"
     * @param username          Username for the database.
     * @param password          Password for the database.
     * @param driverClassName   Fully qualified driver class name like "org.hsqldb.jdbc.JDBCDriver".
     * @param defaultSchemaName Name like "main".
     * @param context           Context to get the tenant identifier from.
     * @param tenantRepository  Repository with all tenants known when the instance is created.
     */
    public TenantRoutingDataSource(final String jdbcUrl,
                                   final String username,
                                   final String password,
                                   final String driverClassName,
                                   final String defaultSchemaName,
                                   final Optional<TenantContext> context,
                                   final TenantRepository tenantRepository) {
        this(jdbcUrl, username, password, driverClassName, defaultSchemaName, new HsqlUrlReplacer(), context, tenantRepository);
    }

    /**
     * Constructor with a custom JDBC url replacer.
     *
     * @param jdbcUrl           JDBC url like "jdbc:hsqldb:mem:main;DB_CLOSE_DELAY=-1"
     * @param username          Username for the database.
     * @param password          Password for the database.
     * @param driverClassName   Fully qualified driver class name like "org.hsqldb.jdbc.JDBCDriver".
     * @param defaultSchemaName Name like "main".
     * @param replacer          Replaces the default database schema with the current tenant schema name.
     * @param context           Context to get the tenant identifier from.
     * @param tenantRepository  Repository with all tenants known when the instance is created.
     */
    public TenantRoutingDataSource(final String jdbcUrl,
                                   final String username,
                                   final String password,
                                   final String driverClassName,
                                   final String defaultSchemaName,
                                   final JdbcUrlReplacer replacer,
                                   final Optional<TenantContext> context,
                                   final TenantRepository tenantRepository) {
        this.jdbcUrl = requireNotEmpty(jdbcUrl, "jdbcUrl");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.driverClassName = requireNotEmpty(driverClassName, "driverClassName");
        this.defaultSchemaName = requireNotEmpty(defaultSchemaName, "defaultSchemaName");
        this.replacer = Objects.requireNonNull(replacer, "replacer==null");
        this.context = context.orElse(Optional::empty);

        final DataSource defaultDataSource = createDefaultDataSource();
        this.dataSourceMap = new ConcurrentHashMap<>();
        tenantRepository.getTenantIds().forEach(
                tenantId -> dataSourceMap.put(tenantId, createDataSource(tenantId))
        );
        this.setTargetDataSources(dataSourceMap);
        this.setDefaultTargetDataSource(defaultDataSource);

    }

    /**
     * Adds a data source for a newly added tenant.
     *
     * @param event Event carrying the added tenant.
     */
    @EventListener
    @Async
    public void handleEvent(TenantAddedEvent event) {
        final TenantId tenantId = event.tenant().getTenantId();
        dataSourceMap.put(tenantId, createDataSource(tenantId));
    }

    /**
     * Removes the data source of a removed tenant.
     *
     * @param event Event carrying the removed tenant.
     */
    @EventListener
    @Async
    public void handleEvent(TenantRemovedEvent event) {
        dataSourceMap.remove(event.tenant().getTenantId());
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return context.getTenantId()
                .map(TenantId::name)
                .orElse(defaultSchemaName);
    }

    private static String requireNotEmpty(String obj, String name) {
        Objects.requireNonNull(obj, name + "==null");
        if (obj.isBlank()) {
            throw new IllegalArgumentException(name + " is empty");
        }
        return obj;
    }

    private DataSource createDefaultDataSource() {
        LOG.info("Creating default data source: {}", jdbcUrl);
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;

    }

    /**
     * Creates a data source for the given tenant by replacing the default schema name in the
     * configured JDBC url with the tenant's schema name.
     *
     * @param tenantId Tenant to create the data source for.
     * @return New tenant specific data source.
     */
    protected DataSource createDataSource(TenantId tenantId) {
        final String tenantUrl = replacer.replace(jdbcUrl, defaultSchemaName, tenantId.name());
        LOG.info("Creating tenant data source: {}", tenantUrl);
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(tenantUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    /**
     * Helper to replace the default schema name with the tenant identifier.
     */
    public interface JdbcUrlReplacer {

        /**
         * Replaces the default JDBC url with the one to use for the tenant.
         *
         * @param jdbcUrl           JDBC url as configured in the settings.
         * @param defaultSchemaName Default schema name.
         * @param tenantSchemaName  Tenant schema name.
         * @return Tenant specific JDBC url.
         */
        String replace(String jdbcUrl, String defaultSchemaName, String tenantSchemaName);

    }

    private static class HsqlUrlReplacer implements JdbcUrlReplacer {

        public String replace(String jdbcUrl, String defaultSchemaName, String tenantSchemaName) {
            return jdbcUrl.replace(":" + defaultSchemaName + ";", ":" + tenantSchemaName + ";");
        }

    }

}
