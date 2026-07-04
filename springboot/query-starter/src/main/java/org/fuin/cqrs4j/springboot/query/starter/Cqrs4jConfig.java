package org.fuin.cqrs4j.springboot.query.starter;

import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.fuin.cqrs4j.springboot.query.core.base.EventstoreConfig;
import org.fuin.cqrs4j.springboot.query.core.view.ProjectionLagMetrics;
import org.fuin.cqrs4j.springboot.query.core.view.SpringViewManager;
import org.fuin.cqrs4j.springboot.query.core.view.SpringViewRegistry;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

/**
 * Configures the necessary beans.
 */
@ThreadSafe
@AutoConfigureBefore(JpaRepositoriesAutoConfiguration.class)
@EnableJpaRepositories(basePackages = {"org.fuin.cqrs4j.springboot.query.core.view"})
@EnableConfigurationProperties({EventstoreConfig.class})
@ComponentScan("org.fuin.cqrs4j.springboot.query.core.view")
@EntityScan("org.fuin.cqrs4j.springboot.query.core.view")
public class Cqrs4jConfig {

    @Bean
    @ConditionalOnMissingBean
    public ViewRegistry viewRegistry(ConfigurableBeanFactory beanFactory, List<View> views) {
        return new SpringViewRegistry(beanFactory, views);
    }

    @Bean
    @Order(0)
    public SpringViewManager viewManager(final ScheduledAnnotationBeanPostProcessor postProcessor,
                                         final ViewRegistry viewRegistry,
                                         final EventStore eventstore,
                                         final ProjectionAdminEventStore admin,
                                         final ProjectionService projectionService,
                                         final PlatformTransactionManager transactionManager,
                                         final ConfigurableBeanFactory beanFactory,
                                         @Value("${org.fuin.cqrs4j.multitenancy:false}") boolean multitenancy,
                                         final Optional<WritableTenantContext> tenantContext,
                                         final Optional<TenantIdsSupplier> tenantIdsSupplier) {

        return new SpringViewManager(postProcessor, viewRegistry, eventstore, admin,
                projectionService, transactionManager, beanFactory,
                multitenancy, tenantContext.orElse(null),
                tenantIdsSupplier.orElse(null));

    }

    /**
     * Registers the projection-lag metrics binder, but only when Micrometer is on the classpath.
     * Applications that do not use Micrometer are unaffected.
     */
    @ThreadSafe
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    public static class ProjectionMetricsConfig {

        /**
         * Binds a projection-lag gauge (tagged with the view name) per registered view.
         *
         * @param viewRegistry      Registered views.
         * @param eventstore        Event store instance to use.
         * @param projectionService Service holding the projection checkpoints.
         * @param multitenancy      Determines if multitenancy is enabled.
         * @param tenantContext     Tenant context.
         * @param tenantIdsSupplier Supplies the known tenant ids.
         * @return Meter binder registered with the application's meter registries.
         */
        @Bean
        @ConditionalOnMissingBean
        public ProjectionLagMetrics projectionLagMetrics(final ViewRegistry viewRegistry,
                                                         final EventStore eventstore,
                                                         final ProjectionService projectionService,
                                                         @Value("${org.fuin.cqrs4j.multitenancy:false}") boolean multitenancy,
                                                         final Optional<WritableTenantContext> tenantContext,
                                                         final Optional<TenantIdsSupplier> tenantIdsSupplier) {
            return new ProjectionLagMetrics(viewRegistry, eventstore, projectionService,
                    multitenancy ? tenantContext.orElse(null) : null,
                    multitenancy ? tenantIdsSupplier.orElse(null) : null);
        }

    }

}
