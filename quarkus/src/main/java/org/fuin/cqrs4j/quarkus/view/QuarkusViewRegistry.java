package org.fuin.cqrs4j.quarkus.view;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.core.SimpleViewRegistry;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.quarkus.base.QuarkusUtils;

import java.lang.annotation.Annotation;
import java.util.List;

@ApplicationScoped
public class QuarkusViewRegistry implements ViewRegistry {

    private final SimpleViewRegistry delegate;

    protected QuarkusViewRegistry() {
        // Required by CDI for the client proxy of this normal-scoped bean. The proxy never
        // serves business calls, so an empty delegate is sufficient and avoids a null field.
        delegate = new SimpleViewRegistry(List.of());
    }

    @Inject
    public QuarkusViewRegistry(BeanManager beanManager, @All final List<View> views) {
        // Verify that all beans are dependent scoped
        for (final View view : views) {
            final Bean<?> bean = QuarkusUtils.findBean(beanManager, view.getBeanName(), view.getBeanClass())
                    .orElseThrow(() -> new IllegalStateException("Bean not found: " + view.getBeanName() + "(" + view.getBeanClass().getName() + ")"));
            final Class<? extends Annotation> scope = bean.getScope();
            if (!scope.equals(Dependent.class)) {
                throw new IllegalStateException("Bean scope must be @Dependent, but was @" + scope.getSimpleName() + " for bean: " + bean.getBeanClass().getName());
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
