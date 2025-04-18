package org.fuin.cqrs4j.test.model;

import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.persistence.AttributeConverter;
import jakarta.annotation.Generated;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;

import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.AsStringCapable;
import org.fuin.objects4j.common.ValueObjectWithBaseType;

import javax.annotation.concurrent.Immutable;

/**
 * The name of the person.
 * 
 * CAUTION: Instances of this type may contain invalid values by deserializing it.
 * This means if you create it from JSON, XML or database (JPA) it may not have a correct length or pattern.
 */
@Generated("Generated class - Manual changes will be overwritten")
@Immutable

public final class PersonName implements ValueObjectWithBaseType<String>, Comparable<PersonName>, Serializable, AsStringCapable {

    private static final long serialVersionUID = 1L;


    /** Minimal length of a valid value. */
    public static final int MIN_LENGTH = 1;

    /** Maximum length of a valid value. */
    public static final int MAX_LENGTH = 200;

    @NotNull
    @PersonNameStr
    private String value;

    /**
     * Protected default constructor for deserialization.
     */
    protected PersonName() {
        super();
    }

    /**
     * Constructor with mandatory data.
     * 
     * @param value
     *            Value.
     */
    public PersonName(final String value) {
        this(value, true);
    }

    private PersonName(final String value, final boolean strict) {
        super();
        if (strict) {
            PersonName.requireArgValid("value", value);
        }
        this.value = value;
    }

    @Override
    public final String asBaseType() {
        return value;
    }

    @Override
    public final String toString() {
        return value;
    }

    @Override
    public final String asString() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PersonName other = (PersonName) obj;
        return Objects.equals(value, other.value);
    }

    @Override
    public final int compareTo(final PersonName other) {
        return value.compareTo(other.value);
    }

    @Override
    @NotNull
    public final Class<String> getBaseType() {
        return String.class;
    }

    /**
     * Verifies that a given string can be converted into the type.
     * 
     * @param value
     *            Value to validate.
     * 
     * @return Returns <code>true</code> if it's a valid type else <code>false</code>.
     */
    public static boolean isValid(final String value) {
        if (value == null) {
            return true;
        }
        if (value.length() < MIN_LENGTH) {
            return false;
        }
        final String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            return false;
        }
        return true;
    }

    /**
     * Verifies if the argument is valid and throws an exception if this is not the case.
     * 
     * @param name
     *            Name of the value for a possible error message.
     * @param value
     *            Value to check.
     * 
     * @throws ConstraintViolationException
     *             The value was not valid.
     */
    public static void requireArgValid(@NotNull final String name, @NotNull final String value) throws ConstraintViolationException {
        if (!isValid(value)) {
            throw new ConstraintViolationException("The argument '" + name + "' is not valid: '" + value + "'");
        }
    }

    /**
     * Ensures that the string can be converted into the type.
     */
    @Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = { Validator.class })
    @Documented
    public static @interface PersonNameStr {

        String message()

        default "{org.fuin.cqrs4j.test.model.PersonName.message}";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

    }

    /**
     * Validates if a string is compliant with the type.
     */
    public static final class Validator implements ConstraintValidator<PersonNameStr, String> {

        @Override
        public final void initialize(final PersonNameStr annotation) {
            // Not used
        }

        @Override
        public final boolean isValid(final String value, final ConstraintValidatorContext context) {
            return PersonName.isValid(value);
        }

    }

    /**
     * Converts the value object from/to string.
     */
    public static final class Converter implements JsonbAdapter<PersonName, String>, AttributeConverter<PersonName, String> {

        private PersonName toVO(final String value) {
            if (value == null) {
                return null;
            }
            return new PersonName(value, false);
        }

        private String fromVO(final PersonName value) {
            if (value == null) {
                return null;
            }
            return value.asBaseType();
        }
        // JSONB Adapter

        @Override
        public final String adaptToJson(final PersonName obj) throws Exception {
            return fromVO(obj);
        }

        @Override
        public final PersonName adaptFromJson(final String str) throws Exception {
            return toVO(str);
        }

        // JPA

        @Override
        public final String convertToDatabaseColumn(final PersonName value) {
            return fromVO(value);
        }

        @Override
        public final PersonName convertToEntityAttribute(final String value) {
            return toVO(value);
        }
    }

}
