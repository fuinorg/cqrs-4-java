package org.fuin.cqrs4j.springboot.test.model;

import org.fuin.cqrs4j.springboot.test.model.PersonId;
import org.fuin.cqrs4j.springboot.test.model.PersonName;
import org.fuin.ddd4j.codegen.api.EventVO;

/**
 * Do not use this class. It's just for generating some code using APT.
 */
@EventVO(pkg="org.fuin.cqrs4j.springboot.test.model",
        name = "PersonCreatedEvent",
        entityIdPathParams = "id",
        description = "A person was created",
        jackson = true,
        serialVersionUID = 1000L,
        entityIdClass = "PersonId",
        message = "MyEvent happened"
)
@SuppressWarnings("java:S1214") // Just a helper to generate code
public interface GEN_PersonCreatedEvent {

    PersonId id = null;

    PersonName name = null;

}
