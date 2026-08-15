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

import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.User;
import org.fuin.utils4j.TestOmitted;

/**
 * An {@link ExecutionContext} with a fixed tenant and user, for tests.
 */
@TestOmitted("Test helper class")
public final class TestExecutionContext implements ExecutionContext {

    private final String subjectId;

    /**
     * Constructor with the default subject.
     */
    public TestExecutionContext() {
        this("subject-1");
    }

    /**
     * Constructor with an explicit subject.
     *
     * @param subjectId Subject id the user reports.
     */
    public TestExecutionContext(final String subjectId) {
        this.subjectId = subjectId;
    }

    @Override
    public TenantId getTenantId() {
        return new TenantId("acme");
    }

    @Override
    public User getUser() {
        return new User() {
            @Override
            public String getUserId() {
                return subjectId;
            }

            @Override
            public String getUserName() {
                return "Jane";
            }
        };
    }

}
