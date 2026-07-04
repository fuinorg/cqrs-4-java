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

import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.ddd4j.core.EventType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link ProjectionStreamIds} class.
 */
public class ProjectionStreamIdsTest {

    @Test
    public void testOfAppendsChecksumToStreamName() {

        // PREPARE
        final Set<EventType> eventTypes = Set.of(new EventType("PersonCreatedEvent"));
        final ViewRegistry.Entry entry = new ViewRegistry.Entry(View.class, "PersonsView", "PersonsViewBean",
                "PersonsProjection", "PersonsStream", "* * * * * *", 100, eventTypes);
        final long checksum = CqrsUtils.calculateAdler32Checksum(eventTypes);

        // TEST & VERIFY
        assertThat(ProjectionStreamIds.of(entry).asString()).isEqualTo("PersonsStream-" + checksum);

    }

}
