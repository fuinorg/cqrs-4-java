package org.fuin.cqrs4j.quarkus.test.model;

import jakarta.validation.constraints.NotNull;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.jsonb.AbstractDomainEvent;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.objects4j.common.Contract;

import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.fuin.objects4j.common.Immutable;
import org.fuin.utils4j.Utils4J;

import org.fuin.cqrs4j.quarkus.test.model.PersonId;
import org.fuin.cqrs4j.quarkus.test.model.PersonName;

/**
 * A person was created.
 */
@Immutable
@HasSerializedDataTypeConstant
public final class PersonCreatedEvent extends AbstractDomainEvent<PersonId> {

    @Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name of the event used to store it - Should never change. */
    public static final EventType TYPE = new EventType(PersonCreatedEvent.class.getSimpleName());

    /** Unique name of the serialized event. */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(TYPE.asBaseType());

    @JsonbProperty("id")
    private PersonId id;

    @JsonbProperty("name")
    private PersonName name;


    /**
     * Protected default constructor for deserialization.
     */
    protected PersonCreatedEvent() { // NOSONAR Default constructor
        super();
    }

    @Override
    public EventType getEventType() {
        return TYPE;
    }

    /**
     * Returns: TODO Add '@Label' annotation.
     *
     * @return TODO Add '@Label' annotation. TODO Add '@Tooltip' annotation.
     */
    @NotNull
    public PersonId getId() {
        return id;
    }
    /**
     * Returns: TODO Add '@Label' annotation.
     *
     * @return TODO Add '@Label' annotation. TODO Add '@Tooltip' annotation.
     */
    @NotNull
    public PersonName getName() {
        return name;
    }

    @Override
    public String toString() {
        final Map<String, String> vars = new HashMap<>();
        vars.put("entityIdPath", getEntityIdPath().toString());
        vars.put("id", "" + id);
        vars.put("name", "" + name);
        return Utils4J.replaceVars("MyEvent happened", vars);
    }

    /**
     * Creates a new builder instance.
     *
     * @return New builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds an instance of the outer class.
     */
    public static final class Builder extends AbstractDomainEvent.Builder<PersonId, PersonCreatedEvent, Builder> {

        private PersonCreatedEvent delegate;

        private Builder() {
            super(new PersonCreatedEvent());
            delegate = delegate();
        }

        /**
         * Sets: TODO Add '@Label' annotation.
         *
         * @param id TODO Add '@Label' annotation.
         * @return This builder.
         */
        @SuppressWarnings("unchecked")
        public final Builder id(@NotNull final PersonId id) {
            Contract.requireArgNotNull("id", id);
            delegate.id = id;
            return this;
        }
        /**
         * Sets: TODO Add '@Label' annotation.
         *
         * @param name TODO Add '@Label' annotation.
         * @return This builder.
         */
        @SuppressWarnings("unchecked")
        public final Builder name(@NotNull final PersonName name) {
            Contract.requireArgNotNull("name", name);
            delegate.name = name;
            return this;
        }

        /**
         * Creates the event and clears the builder.
         *
         * @return New instance.
         */
        public PersonCreatedEvent build() {
            ensureBuildableAbstractDomainEvent();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            final PersonCreatedEvent result = delegate;
            delegate = new PersonCreatedEvent();
            resetAbstractDomainEvent(delegate);
            return result;
        }

    }

}
