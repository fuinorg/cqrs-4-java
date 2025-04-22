package org.fuin.cqrs4j.quarkus.test.model;

import org.fuin.ddd4j.codegen.api.AggregateRootUuidVO;

/**
 * Do not use this class. It's just for generating some code using APT.
 */
@AggregateRootUuidVO(
        pkg="org.fuin.cqrs4j.quarkus.test.model",
        name = "PersonId",
        entityType = "PERSON",
        description = "Unique identifier of a person",
        jsonb = true, jpa = true,
        serialVersionUID = 1000L,
        example = "b20d7373-1950-478a-ab61-d022cd44f507"
)
public interface GEN_PersonId {
}
