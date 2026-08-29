/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.jackson;

import org.fuin.cqrs4j.core.CommandDeliveryException;
import org.fuin.cqrs4j.core.Result;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.ddd4j.jackson.AggregateNotFoundExceptionData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for the {@link Results} class.
 */
class ResultsTest {

    @Test
    void testErrorWithData() {

        // An exception from another library entirely: its data class is found through the Jandex index
        // shipped in that library's jar, so nothing here has to know the exception exists.
        final AggregateNotFoundException ex = new AggregateNotFoundException("CUSTOMER", "4321");

        final Result<Object> result = Results.error(ex);

        assertThat(result).isInstanceOf(DataResult.class);
        assertThat(result.getType()).isEqualTo(ResultType.ERROR);
        // The kind of problem, readable because this exception carries a short id. An exception without
        // one is identified by its full qualified class name instead - see the second test.
        assertThat(result.getCode()).isEqualTo("DDD4J-AGGREGATE_NOT_FOUND");
        assertThat(result.getMessage()).isEqualTo("CUSTOMER with id 4321 not found");
        final DataResult<Object> dataResult = (DataResult<Object>) result;
        assertThat(dataResult.getDataClass()).isEqualTo(AggregateNotFoundExceptionData.class.getName());
        assertThat(dataResult.getData()).isInstanceOf(AggregateNotFoundExceptionData.class);

    }

    @Test
    void testErrorWithoutData() {

        // The normal case: no data class is registered, so the refusal travels as code and message.
        final CommandDeliveryException ex = new CommandDeliveryException("Delivery failed", 500, new RuntimeException("Boom"));

        final Result<Object> result = Results.error(ex);

        assertThat(result).isInstanceOf(SimpleResult.class);
        assertThat(result.getType()).isEqualTo(ResultType.ERROR);
        // No short id either, so the class name is what identifies the kind.
        assertThat(result.getCode()).isEqualTo(CommandDeliveryException.class.getName());

    }

}
