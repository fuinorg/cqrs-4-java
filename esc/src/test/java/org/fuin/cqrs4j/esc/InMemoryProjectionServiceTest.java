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

import org.fuin.esc.api.ProjectionStreamId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryProjectionService}.
 */
public class InMemoryProjectionServiceTest {

    private static final ProjectionStreamId STREAM = new ProjectionStreamId("AuthorizationProjection");

    @Test
    public void testUnknownStreamStartsAtZero() {
        // The whole point: a fresh process replays from the beginning, because its state is in memory and
        // was not rebuilt from anywhere.
        assertThat(new InMemoryProjectionService().readProjectionPosition(STREAM)).isZero();
    }

    @Test
    public void testUpdateAndRead() {
        final InMemoryProjectionService testee = new InMemoryProjectionService();
        testee.updateProjectionPosition(STREAM, 42L);
        assertThat(testee.readProjectionPosition(STREAM)).isEqualTo(42L);
    }

    @Test
    public void testReset() {
        final InMemoryProjectionService testee = new InMemoryProjectionService();
        testee.updateProjectionPosition(STREAM, 42L);
        testee.resetProjectionPosition(STREAM);
        assertThat(testee.readProjectionPosition(STREAM)).isZero();
    }

    @Test
    public void testStreamsAreIndependent() {
        final InMemoryProjectionService testee = new InMemoryProjectionService();
        testee.updateProjectionPosition(STREAM, 42L);
        assertThat(testee.readProjectionPosition(new ProjectionStreamId("Other"))).isZero();
    }

}
