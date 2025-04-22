package org.fuin.cqrs4j.quarkus.test.model;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.fuin.objects4j.jsonb.UUIDJsonbAdapter;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a person. Equals/hashCode are based on the ID only and the entity is sorted by the name.
 */
@Entity(name = "PERSON")
public class PersonEntity implements Comparable<PersonEntity> {

    @Id
    @Column(name = "ID", unique = true, nullable = false)
    @JsonbProperty
    @JsonbTypeAdapter(UUIDJsonbAdapter.class)
    public UUID id;

    @Column(name = "NAME", nullable = false, length = PersonName.MAX_LENGTH)
    @JsonbProperty
    public String name;

    /**
     * Constructor used by JPA & JSON-B.
     */
    protected PersonEntity() {
    }

    /**
     * Constructor with mandatory data.
     *
     * @param id   Unique identifier of the person.
     * @param name Name of the person.
     */
    public PersonEntity(PersonId id, PersonName name) {
        this.id = Objects.requireNonNull(id, "id==null").asBaseType();
        this.name = Objects.requireNonNull(name, "name==null").asBaseType();
    }

    /**
     * Returns the unique identifier of the person.
     *
     * @return Person ID.
     */
    public PersonId getId() {
        return new PersonId(id);
    }

    /**
     * Returns the name of the person.
     *
     * @return Person name.
     */
    public PersonName getName() {
        return new PersonName(name);
    }

    @Override
    public int compareTo(PersonEntity o) {
        return this.name.compareTo(o.name);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PersonEntity that = (PersonEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "PersonEntity{" +
                "id=" + id +
                ", name=" + name +
                '}';
    }

}
