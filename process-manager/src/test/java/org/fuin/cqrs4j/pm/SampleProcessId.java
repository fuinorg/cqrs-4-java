package org.fuin.cqrs4j.pm;

import org.fuin.ddd4j.core.AggregateRootUuid;
import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.core.StringBasedEntityType;
import org.fuin.utils4j.TestOmitted;

import java.io.Serial;
import java.util.UUID;

/**
 * Identifier of the sample process manager (also used as the entity id of the sample domain events, so the
 * correlation is simply the event's entity id).
 */
@TestOmitted("Sample process manager id")
final class SampleProcessId extends AggregateRootUuid {

    @Serial
    private static final long serialVersionUID = 1L;

    static final EntityType TYPE = new StringBasedEntityType("SAMPLE_PROCESS");

    SampleProcessId() {
        super(TYPE);
    }

    SampleProcessId(final UUID uuid) {
        super(TYPE, uuid);
    }

}
