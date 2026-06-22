package org.fuin.cqrs4j.quarkus.base;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import org.fuin.objects4j.common.ThreadSafe;

import java.util.Optional;
import java.util.Set;

/**
 * Helper routines related to Quarkus.
 */
@ThreadSafe
public final class QuarkusUtils {

    private QuarkusUtils() {
    }

    /**
     * Tties to find a bean by class and name.
     *
     * @param beanManager Bean manager to use for finding.
     * @param beanName Name of the bean to find.
     * @param beanClass Type of the bean to find.
     * @return Bean definition.
     */
    public static Optional<Bean<?>> findBean(BeanManager beanManager, String beanName, Class<?> beanClass) {
        final Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        if (beans.isEmpty()) {
            throw new IllegalStateException("There is no bean of type " + beanClass.getName());
        }
        if (beans.size() > 1) {
            throw new IllegalStateException("There more than one bean of type " + beanClass.getName());
        }
        return beans.stream().filter(b -> b.getName().equals(beanName)).findFirst();
    }

}
