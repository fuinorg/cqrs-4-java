package org.fuin.cqrs4j.springboot.test.pm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

/**
 * State of the {@link SampleProcessManagerView}. Persisted in the same transaction as the command(s)
 * the process manager produces.
 */
@Entity(name = "SAMPLE_PM_STATE")
public class SampleProcessManagerState {

    @Id
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    /**
     * Constructor used by JPA.
     */
    protected SampleProcessManagerState() {
    }

    /**
     * Constructor with mandatory data.
     *
     * @param id   Unique identifier (the person the process is about).
     * @param name Name copied from the triggering event.
     */
    public SampleProcessManagerState(final UUID id, final String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns the unique identifier.
     *
     * @return ID.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the name.
     *
     * @return Name.
     */
    public String getName() {
        return name;
    }

}
