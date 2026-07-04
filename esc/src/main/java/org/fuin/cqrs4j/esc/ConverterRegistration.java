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
package org.fuin.cqrs4j.esc;

import org.fuin.esc.api.Converter;
import org.fuin.esc.api.ConverterRegistry;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.esc.api.SimpleConverterRegistry;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.Immutable;

import java.util.Collection;

/**
 * Describes a single event up-caster: a {@link Converter} that lifts an event of a given
 * {@link SerializedDataType} from one version to the next. Applications expose these as beans (Spring
 * {@code List<ConverterRegistration>} / Quarkus {@code @All List<ConverterRegistration>}); the framework
 * collects them via {@link #toRegistry(Collection)} into a {@link ConverterRegistry} that decorates the event
 * store's deserialization so projections and aggregate replay upcast automatically.
 *
 * @param type        Logical type of the event the converter applies to.
 * @param fromVersion Source version the converter accepts.
 * @param toVersion   Target version the converter produces.
 * @param converter   Converter performing the conversion.
 */
@Immutable
public record ConverterRegistration(SerializedDataType type, String fromVersion, String toVersion,
                                    Converter<?, ?> converter) {

    /**
     * Compact constructor validating that all components are set.
     */
    public ConverterRegistration {
        Contract.requireArgNotNull("type", type);
        Contract.requireArgNotNull("fromVersion", fromVersion);
        Contract.requireArgNotNull("toVersion", toVersion);
        Contract.requireArgNotNull("converter", converter);
    }

    /**
     * Folds the given registrations into a converter registry. An empty collection yields an empty registry
     * that passes events through unchanged.
     *
     * @param registrations Registrations to fold (may be empty, but not {@literal null}).
     * @return New converter registry.
     */
    public static ConverterRegistry toRegistry(final Collection<ConverterRegistration> registrations) {
        Contract.requireArgNotNull("registrations", registrations);
        final SimpleConverterRegistry.Builder builder = new SimpleConverterRegistry.Builder();
        for (final ConverterRegistration registration : registrations) {
            builder.add(registration.type(), registration.fromVersion(), registration.toVersion(),
                    registration.converter());
        }
        return builder.build();
    }

}
