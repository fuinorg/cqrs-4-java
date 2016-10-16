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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.fuin.ddd4j.ddd.EventType;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.junit.Test;

/**
 * Test for {@link MultiCommandExecutor}.
 */
public class MultiCommandExecutorTest {

    @SuppressWarnings("rawtypes")
    @Test
    public final void testDispatch() {

        // PREPARE
        final CountDownLatch done = new CountDownLatch(2);
        final CommandExecutor cmdHandler1 = new CommandExecutor<Long, MyCommand>() {
            @Override
            public final Set<EventType> getCommandTypes() {
                final Set<EventType> set = new HashSet<>();
                set.add(MyCommand.EVENT_TYPE);
                return set;
            }

            @Override
            public final Long execute(final MyCommand cmd) {
                done.countDown();
                return done.getCount();
            }

        };
        final List<CommandExecutor> list = new ArrayList<>();
        list.add(cmdHandler1);
        final MultiCommandExecutor testee = new MultiCommandExecutor(list);

        // TEST call twice
        testee.execute(new MyCommand());
        testee.execute(new MyCommand());

        // VERIFY
        assertThat(done.getCount()).isEqualTo(0);

    }

    @Test
    public final void testCreateNullArray() {

        try {
            new MultiCommandExecutor();
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'cmdExecutors' cannot be an empty list");
        }

    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void testCreateNullList() {

        try {
            new MultiCommandExecutor((List<CommandExecutor>) null);
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'cmdExecutors' cannot be null");
        }

    }

    @Test
    public final void testCreateEmptyArray() {

        try {
            new MultiCommandExecutor(new CommandExecutor[] {});
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'cmdExecutors' cannot be an empty list");
        }

    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void testCreateEmptyList() {

        try {
            new MultiCommandExecutor(new ArrayList<CommandExecutor>());
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage()).isEqualTo("The argument 'cmdExecutors' cannot be an empty list");
        }

    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void testCreateDuplicate() {

        // PREPARE
        final CommandExecutor cmdHandler1 = new CommandExecutor<Void, MyCommand>() {
            @Override
            public final Set<EventType> getCommandTypes() {
                final Set<EventType> set = new HashSet<>();
                set.add(MyCommand.EVENT_TYPE);
                return set;
            }

            @Override
            public final Void execute(final MyCommand event) {
                return null;
            }
        };
        final CommandExecutor cmdHandler2 = new CommandExecutor<Void, MyCommand>() {
            @Override
            public final Set<EventType> getCommandTypes() {
                final Set<EventType> set = new HashSet<>();
                set.add(MyCommand.EVENT_TYPE);
                return set;
            }

            @Override
            public final Void execute(final MyCommand event) {
                return null;
            }
        };
        final List<CommandExecutor> list = new ArrayList<>();
        list.add(cmdHandler1);
        list.add(cmdHandler2);

        // TEST
        try {
            new MultiCommandExecutor(list);
            fail();
        } catch (final ConstraintViolationException ex) {
            assertThat(ex.getMessage())
                    .isEqualTo("The argument 'cmdExecutors' contains multiple executors for command: "
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
