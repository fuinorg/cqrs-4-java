package org.fuin.cqrs4j.quarkus.view;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.fuin.cqrs4j.esc.ConverterRegistration;
import org.fuin.esc.api.ConverterRegistry;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * Collects the application-provided event up-caster registrations into a {@link ConverterRegistry} used to
 * decorate the event store's deserialization (so projections and replay upcast old-version events). Empty when
 * the application registers no {@link ConverterRegistration} beans, in which case events pass through
 * unchanged.
 */
@ThreadSafe
@ApplicationScoped
public class QuarkusConverterRegistryProducer {

    /**
     * Produces the converter registry from all registered {@link ConverterRegistration} beans.
     *
     * @param registrations Converter registrations contributed by the application (may be empty).
     * @return Converter registry.
     */
    @Produces
    @Singleton
    public ConverterRegistry converterRegistry(@All final List<ConverterRegistration> registrations) {
        return ConverterRegistration.toRegistry(registrations);
    }

}
