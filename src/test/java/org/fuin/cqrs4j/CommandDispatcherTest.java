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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.junit.Test;

/**
 * Test for {@link CommandDispatcher}.
 */
public class CommandDispatcherTest {

    @SuppressWarnings("rawtypes")
    @Test
    public final void testDispatch() {

        // PREPARE
        final CountDownLatch done = new CountDownLatch(2);
        final CommandHandler cmdHandler1 = new CommandHandler<MyCommand>() {
            @Override
            public final EventType getEventType() {
                return MyCommand.EVENT_TYPE;
            }

            @Override
            public final void handle(final MyCommand event) {
                done.countDown();
            }
        };
        final List<CommandHandler> list = new ArrayList<>();
        list.add(cmdHandler1);
        final CommandDispatcher testee = new CommandDispatcher(list);

        // TEST call twice
        testee.dispatch(new MyCommand());
        testee.dispatch(new MyCommand());

        // VERIFY
        assertThat(done.getCount()).isEqualTo(0);

    }

    @Test
    public final void testCreateNullArray() {

        try {
            new CommandDispatcher();
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'commandHandlers' cannot be an empty list");
        }

    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void testCreateNullList() {

        try {
            new CommandDispatcher((List<CommandHandler>) null);
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'commandHandlers' cannot be null");
        }

    }

    @Test
    public final void testCreateEmptyArray() {

        try {
            new CommandDispatcher(new CommandHandler[] {});
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'commandHandlers' cannot be an empty list");
        }

    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void testCreateEmptyList() {

        try {
            new CommandDispatcher(new ArrayList<CommandHandler>());
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'commandHandlers' cannot be an empty list");
        }

    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void testCreateDuplicate() {

        // PREPARE
        final CommandHandler cmdHandler1 = new CommandHandler<MyCommand>() {
            @Override
            public final EventType getEventType() {
                return MyCommand.EVENT_TYPE;
            }

            @Override
            public final void handle(final MyCommand event) {
                // Do nothing
            }
        };
        final CommandHandler cmdHandler2 = new CommandHandler<MyCommand>() {
            @Override
            public final EventType getEventType() {
                return MyCommand.EVENT_TYPE;
            }

            @Override
            public final void handle(final MyCommand event) {
                // Do nothing
            }
        };
        final List<CommandHandler> list = new ArrayList<>();
        list.add(cmdHandler1);
        list.add(cmdHandler2);

        // TEST
        try {
            new CommandDispatcher(list);
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage())
                    .isEqualTo("The argument 'commandHandlers' contains multiple handlers for event: "
                            + MyCommand.EVENT_TYPE);
        }

    }

    public static class MyCommand extends AbstractCommand {

        private static final long serialVersionUID = 1L;

        private static final EventType EVENT_TYPE = new EventType("MyCommand");

        public MyCommand() {
            super();
        }

        @Override
        public EventType getEventType() {
            return EVENT_TYPE;
        }

    }

}
