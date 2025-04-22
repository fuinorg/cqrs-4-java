package org.fuin.cqrs4j.quarkus.test.model;

import org.fuin.ddd4j.codegen.api.StringVO;

/**
 * Do not use this class. It's just for generating some code using APT.
 */
@StringVO(
        pkg="org.fuin.cqrs4j.quarkus.test.model",
        name = "PersonName",
        description = "The name of the person",
        jsonb = true, jpa = true,
        minLength = 1, maxLength = 200
)
public interface GEN_PersonName {

}
