package org.fuin.cqrs4j.springboot.query.core.view;

import org.fuin.cqrs4j.core.SimpleViewRegistry;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import java.util.List;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * Registry that verifies that views have 'prototype' scope.
 */
public class SpringViewRegistry implements ViewRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SpringViewRegistry.class);

    private final SimpleViewRegistry delegate;

    /**
     * Constructor with mandatory data.
     *
     * @param beanFactory Bean factory used to get bean definitions from-
     * @param views Known views.
     */
    public SpringViewRegistry(ConfigurableBeanFactory beanFactory, List<View> views) {
        // Verify that all beans are dependent scoped
        LOG.info("Found {} views", views.size());
        for (final View view : views) {
            LOG.info("View: {}", view.getBeanName());
            final BeanDefinition beanDefinition = beanFactory.getMergedBeanDefinition(view.getBeanName());
            final String scope = beanDefinition.getScope();
            if (!SCOPE_PROTOTYPE.equals(scope)) {
                throw new IllegalStateException("Bean scope must be @Scope(SCOPE_PROTOTYPE), but was @Scope("
                        + scope + ") for bean: " + view.getBeanClass().getName());
            }
        }
        delegate = new SimpleViewRegistry(views);
    }

    @Override
    public List<Entry> getViews() {
        return delegate.getViews();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

}
