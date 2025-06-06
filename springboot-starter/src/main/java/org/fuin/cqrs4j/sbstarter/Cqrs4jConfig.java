package org.fuin.cqrs4j.sbstarter;

import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.springboot.base.EventstoreConfig;
import org.fuin.cqrs4j.springboot.view.SpringViewManager;
import org.fuin.cqrs4j.springboot.view.SpringViewRegistry;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

/**
 * Configures the necessary beans.
 */
@AutoConfigureBefore(JpaRepositoriesAutoConfiguration.class)
@EnableJpaRepositories(basePackages = {"org.fuin.cqrs4j.springboot.view"})
@EnableConfigurationProperties({EventstoreConfig.class})
@ComponentScan("org.fuin.cqrs4j.springboot.view")
@EntityScan("org.fuin.cqrs4j.springboot.view")
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
                                         final ConfigurableBeanFactory beanFactory) {

        return new SpringViewManager(postProcessor, viewRegistry, eventstore, admin,
                projectionService, transactionManager, beanFactory);

    }

}
