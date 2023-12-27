/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved. 
 * http://www.fuin.org/
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.AggregateVersion;
import org.fuin.ddd4j.ddd.EntityIdPath;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.vo.EmailAddressStr;
import org.junit.jupiter.api.Test;

// CHECKSTYLE:OFF
public final class Cqrs4JUtilsTest {

    @Test
    public void testVerifyPrecondition() {

        final MyClass myObj1 = new MyClass();
        assertThat(Cqrs4JUtils.verifyPrecondition(Contract.getValidator(), myObj1))
                .isEqualTo(new SimpleResult(ResultType.ERROR, Cqrs4JUtils.PRECONDITION_VIOLATED, "MyClass.email must not be null"));

        final MyClass myObj2 = new MyClass();
        myObj2.email = "test@fuin.org";
        assertThat(Cqrs4JUtils.verifyPrecondition(Contract.getValidator(), myObj2)).isNull();

    }

    @Test
    public void testVerifyParamIdEqualsCmdAggregateId() {

        // PREPARE
        AId aid1 = new AId(1L);
        AggregateVersion version = new AggregateVersion(0);
        BId bid123 = new BId(123L);
        AId aid2 = new AId(2L);
        final AggregateCommand<AId,BId> cmd = new MyCommand(aid1, version, bid123);

        // TEST & VERIFY
        assertThat(Cqrs4JUtils.verifyParamEntityIdPathEqualsCmdEntityIdPath(cmd, aid1, bid123)).isNull();
        assertThat(Cqrs4JUtils.verifyParamEntityIdPathEqualsCmdEntityIdPath(cmd, aid1))
                .isEqualTo(new SimpleResult(ResultType.ERROR, Cqrs4JUtils.PARAM_ENTITY_PATH_NOT_EQUAL_CMD_ENTITY_PATH,
                        "Entity path constructred from URL parameters A 1 is not the same as command's entityPath: 'A 1/B 123'"));
        assertThat(Cqrs4JUtils.verifyParamEntityIdPathEqualsCmdEntityIdPath(cmd, aid2, bid123))
                .isEqualTo(new SimpleResult(ResultType.ERROR, Cqrs4JUtils.PARAM_ENTITY_PATH_NOT_EQUAL_CMD_ENTITY_PATH,
                        "Entity path constructred from URL parameters A 2, B 123 is not the same as command's entityPath: 'A 1/B 123'"));

    }

    private static class MyClass {

        @NotNull
        @EmailAddressStr
        private String email;

    }

    private static class MyCommand extends AbstractAggregateCommand<AId, BId> {

        private static final long serialVersionUID = 1L;

        public MyCommand(AId id, AggregateVersion aggregateVersion, final BId bid) {
            super(new EntityIdPath(id, bid), aggregateVersion);
        }

        @Override
        @NotNull
        public EventType getEventType() {
            return new EventType("MyCommand");
        }

    }

}
// CHECKSTYLE:ON
