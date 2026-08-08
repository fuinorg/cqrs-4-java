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
package org.fuin.cqrs4j.esc;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.ddd4j.core.AggregateRoot;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.Repository;
import org.fuin.ddd4j.core.User;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link AuditedRepository}.
 */
class AuditedRepositoryTest {

    private static final String SUBJECT_ID = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6";

    @Test
    void testAddPassesTheActingUserAsMetadata() throws Exception {

        // PREPARE
        @SuppressWarnings("unchecked")
        final Repository<AggregateRootId, AggregateRoot<AggregateRootId>> repository = mock(Repository.class);
        final AggregateRoot<AggregateRootId> aggregate = mock(AggregateRoot.class);

        // TEST
        AuditedRepository.add(repository, aggregate, context(SUBJECT_ID));

        // VERIFY - the three-argument overload, never the bare one
        verify(repository).add(same(aggregate), eq("CommandMeta"), eq(new CommandMeta(SUBJECT_ID)));

    }

    @Test
    void testUpdatePassesTheActingUserAsMetadata() throws Exception {

        // PREPARE
        @SuppressWarnings("unchecked")
        final Repository<AggregateRootId, AggregateRoot<AggregateRootId>> repository = mock(Repository.class);
        final AggregateRoot<AggregateRootId> aggregate = mock(AggregateRoot.class);

        // TEST
        AuditedRepository.update(repository, aggregate, context(SUBJECT_ID));

        // VERIFY
        verify(repository).update(same(aggregate), eq("CommandMeta"), eq(new CommandMeta(SUBJECT_ID)));

    }

    private static CommandExecutionContext context(final String subjectId) {
        final User user = mock(User.class);
        when(user.getUserId()).thenReturn(subjectId);
        final CommandExecutionContext context = mock(CommandExecutionContext.class);
        when(context.getUser()).thenReturn(user);
        return context;
    }

}
