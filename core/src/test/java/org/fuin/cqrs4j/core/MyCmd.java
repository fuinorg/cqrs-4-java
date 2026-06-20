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
package org.fuin.cqrs4j.core;

import org.fuin.utils4j.TestOmitted;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;

import java.time.ZonedDateTime;

@TestOmitted("Test helper class")
public class MyCmd implements Command {
    @Override
    public @NotNull EventId getEventId() {
        return null;
    }

    @Override
    public @NotNull EventType getEventType() {
        return null;
    }

    @Override
    public @NotNull ZonedDateTime getEventTimestamp() {
        return null;
    }

    @Nullable
    @Override
    public EventId getCorrelationId() {
        return null;
    }

    @Nullable
    @Override
    public EventId getCausationId() {
        return null;
    }
}
