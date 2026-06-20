package org.fuin.cqrs4j.springboot.query.core.base;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link EventstoreConfig} class.
 */
class EventstoreConfigTest {

    private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void testConstructionNull() {
        final EventstoreConfig testee = new EventstoreConfig(null, null, null, false);
        assertThat(testee.isTls()).isFalse();
        assertThat(testee.getHost()).isEqualTo("localhost");
        assertThat(testee.getPort()).isEqualTo(2113);
        assertThat(testee.isTenantMode()).isFalse();
        assertThat(validator.validate(testee)).isEmpty();
    }

    @Test
    void testHostError() {
        assertThat(validator.validate(new EventstoreConfig(null, "", null, false)))
                .allMatch(violation ->
                        violation.getMessage().contains("size must be between 1 and 235"));
    }

    @Test
    void testPortError() {
        assertThat(validator.validate(new EventstoreConfig(null, null, 100, false)))
                .allMatch(violation ->
                        violation.getMessage().contains("must be greater than or equal to 1024"));
    }

}