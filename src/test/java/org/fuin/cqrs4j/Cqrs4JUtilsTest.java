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

import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.AggregateVersion;
import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.vo.EmailAddressStr;
import org.junit.Test;

// CHECKSTYLE:OFF
public final class Cqrs4JUtilsTest {

    @Test
    public void testVerifyPrecondition() {

        final MyClass myObj1 = new MyClass();
        assertThat(Cqrs4JUtils.verifyPrecondition(Contract.getValidator(), myObj1))
                .isEqualTo(new Result<>(ResultType.ERROR, Cqrs4JUtils.PRECONDITION_VIOLATED, "MyClass.email must not be null", null));

        final MyClass myObj2 = new MyClass();
        myObj2.email = "test@fuin.org";
        assertThat(Cqrs4JUtils.verifyPrecondition(Contract.getValidator(), myObj2)).isNull();

    }

    @Test
    public void testVerifyParamIdEqualsCmdAggregateId() {

        // PREPARE
        AId aid1 = new AId(1L);
        AggregateVersion version = new AggregateVersion(0);
        AId aid2 = new AId(2L);
        final AggregateCommand<AId> cmd = new MyCommand(aid1, version);

        // TEST & VERIFY
        assertThat(Cqrs4JUtils.verifyParamIdEqualsCmdAggregateId(aid1, cmd)).isNull();
        assertThat(Cqrs4JUtils.verifyParamIdEqualsCmdAggregateId(aid2, cmd))
                .isEqualTo(new Result<>(ResultType.ERROR, Cqrs4JUtils.PARAM_ID_NOT_EQUAL_CMD_AGGREGATE_ID,
                        "URL parameter ID 'AId [id=2]' is not the same as command's aggregate root ID: 'AId [id=1]'", null));

    }

    private static class MyClass {

        @NotNull
        @EmailAddressStr
        private String email;

    }

    private static class MyCommand extends AbstractAggregateCommand<AId> {

        private static final long serialVersionUID = 1L;

        public MyCommand(AId id, AggregateVersion aggregateVersion) {
            super(id, aggregateVersion);
        }

        @Override
        @NotNull
        public EventType getEventType() {
            return new EventType("MyCommand");
        }

    }

}
// CHECKSTYLE:ON
