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

/**
 * Transactional-outbox based process manager support for Spring Boot. A process manager is a normal
 * {@link org.fuin.cqrs4j.core.View} (driven by the existing Spring view engine) that updates its own
 * state and enqueues commands into an outbox table within the same transaction. The
 * {@link org.fuin.cqrs4j.springboot.pm.core.CommandQueueExecutor} drains that outbox asynchronously,
 * POSTing each command to a configured command endpoint, deleting it on success and moving it to a
 * dead-letter table once it fails durably.
 */
@NullMarked
package org.fuin.cqrs4j.springboot.pm.core;

import org.jspecify.annotations.NullMarked;
