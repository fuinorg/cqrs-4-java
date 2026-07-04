package org.fuin.cqrs4j.quarkus.test.pm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.fuin.objects4j.common.NotThreadSafe;

import java.util.UUID;

/**
 * Minimal process-manager state: the presence of a row (keyed by the person id) marks that the sample process
 * manager has already reacted to that person, so it reacts (and sends its command) only once.
 */
@NotThreadSafe
@Entity
@Table(name = "QUARKUS_SAMPLE_PM_STATE")
public class SampleProcessManagerState {

    @Id
    @Column(name = "ID", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    /**
     * JPA constructor.
     */
    protected SampleProcessManagerState() {
        super();
    }

    /**
     * Constructor with mandatory data.
     *
     * @param id   Person id the process is about.
     * @param name Person name (copied from the triggering event).
     */
    public SampleProcessManagerState(final UUID id, final String name) {
        super();
        this.id = id;
        this.name = name;
    }

    /**
     * Returns the person id.
     *
     * @return Id.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the person name.
     *
     * @return Name.
     */
    public String getName() {
        return name;
    }

}
