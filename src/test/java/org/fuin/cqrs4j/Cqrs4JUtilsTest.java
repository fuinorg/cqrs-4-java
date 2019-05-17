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

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.vo.EmailAddressStr;
import org.junit.Test;

// CHECKSTYLE:OFF
public final class Cqrs4JUtilsTest {

    @Test
    public void testVerifyPrecondition() {

        final MyClass myObj1 = new MyClass();
        assertThat(Cqrs4JUtils.verifyPrecondition(Contract.getValidator(), myObj1)).isEqualTo(new Result<>(ResultType.ERROR,
                Cqrs4JUtils.PRECONDITION_VIOLATED, "MyClass.email must not be null, MyClass.name must not be empty", null));

        final MyClass myObj2 = new MyClass();
        myObj2.email = "test@fuin.org";
        myObj2.name = "test";
        assertThat(Cqrs4JUtils.verifyPrecondition(Contract.getValidator(), myObj2)).isNull();

    }

    @Test
    public void testVerifyParamIdEqualsCmdAggregateId() {

        // PREPARE
        AId aid1 = new AId(1L);
        AId aid2 = new AId(2L);
        final DomainCommand<AId> cmd = new AbstractDomainCommand<AId>() {
            private static final long serialVersionUID = 1L;

            @Override
            @NotNull
            public AId getAggregateRootId() {
                return aid1;
            }

            @Override
            @NotNull
            public EventType getEventType() {
                return new EventType("MyCommand");
            }
        };

        // TEST & VERIFY
        assertThat(Cqrs4JUtils.verifyParamIdEqualsCmdAggregateId(aid1, cmd)).isNull();
        assertThat(Cqrs4JUtils.verifyParamIdEqualsCmdAggregateId(aid2, cmd))
                .isEqualTo(new Result<>(ResultType.ERROR, Cqrs4JUtils.PARAM_ID_NOT_EQUAL_CMD_AGGREGATE_ID,
                        "URL parameter ID 'AId [id=2]' is not the same as command's aggregate root ID: 'AId [id=1]'", null));

    }

    private static class MyClass {

        @NotEmpty
        private String name;

        @NotNull
        @EmailAddressStr
        private String email;

    }

}
// CHECKSTYLE:ON
