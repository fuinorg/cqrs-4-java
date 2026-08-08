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

import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.esc.api.TypeName;
import org.fuin.objects4j.common.ImmutableAfterUnmarshal;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Event metadata recording <b>who</b> caused the events of one command execution. It is written once per
 * save and shared by all events of that save.
 * <p>
 * <b>It carries the acting user's opaque subject id and nothing else</b> - no name, no e-mail address, no
 * preferred username, no other personal attribute. That omission is the point, not an oversight: events
 * are immutable, so any personal data written into one can never be reached by a deletion request. An
 * identity provider's subject id is a <em>reference</em> - deleting the user there makes it stop
 * resolving to anybody, which is what makes erasure possible at all while the events themselves stay
 * under whatever retention rules apply to them. Resolve names and addresses for display from the
 * identity provider at read time.
 * <p>
 * Deliberately free of any serialization annotations: this module has neither Jackson nor JSON-B on its
 * classpath, and none are needed. Both ESC stacks bind by field access - Jackson with {@code FIELD}
 * visibility {@code ANY}, JSON-B with a {@code FieldAccessStrategy} - so the shape below (no-arg
 * constructor plus a framework-written field) is enough for either. Bound with a default-configured
 * mapper instead, the type would serialize correctly and deserialize to {@code null}; the tests pin
 * both configurations for that reason. The type registers itself through {@link HasSerializedDataTypeConstant} and
 * the {@link #SER_TYPE} constant, so an application whose registry is built by scanning the Jandex index
 * - which reads every {@code META-INF/jandex.idx} on the classpath, including this jar's - picks it up
 * with no configuration at all.
 *
 * @see AuditedRepository
 */
@ImmutableAfterUnmarshal
@HasSerializedDataTypeConstant
public final class CommandMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name of the meta type. */
    public static final TypeName TYPE = new TypeName("CommandMeta");

    /**
     * Type used to look up the serializer and deserializer. The registry is built by scanning for the
     * annotation on this class, so without this constant the type is unknown at runtime and neither
     * storing it nor reading it back works.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(TYPE.asBaseType());

    @SuppressWarnings("NullAway.Init")
    private String subjectId;

    /**
     * Protected default constructor for deserialization.
     */
    @SuppressWarnings("NullAway.Init")
    protected CommandMeta() {
        super();
    }

    /**
     * Constructor with all mandatory data.
     *
     * @param subjectId Opaque identifier of the acting user - the OpenID Connect {@code sub} claim when
     *                  the caller is authenticated. Never a name or an e-mail address.
     */
    public CommandMeta(final String subjectId) {
        super();
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId==null");
    }

    /**
     * Returns the opaque identifier of the acting user.
     *
     * @return Subject id, never {@code null} on a properly populated instance.
     */
    public String getSubjectId() {
        return subjectId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(subjectId);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CommandMeta other)) {
            return false;
        }
        return Objects.equals(subjectId, other.subjectId);
    }

    @Override
    public String toString() {
        return "CommandMeta{subjectId=" + subjectId + "}";
    }

}
