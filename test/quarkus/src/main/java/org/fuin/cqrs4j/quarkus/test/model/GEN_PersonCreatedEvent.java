package org.fuin.cqrs4j.quarkus.test.model;

import org.fuin.ddd4j.codegen.api.EventVO;

/**
 * Do not use this class. It's just for generating some code using APT.
 */
@EventVO(pkg="org.fuin.cqrs4j.quarkus.test.model",
        name = "PersonCreatedEvent",
        entityIdPathParams = "id",
        description = "A person was created",
        jsonb = true,
        serialVersionUID = 1000L,
        entityIdClass = "PersonId",
        message = "MyEvent happened"
)
public interface GEN_PersonCreatedEvent {

    PersonId id = null;

    PersonName name = null;

}
