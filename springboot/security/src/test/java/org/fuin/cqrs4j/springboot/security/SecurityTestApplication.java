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

package org.fuin.cqrs4j.springboot.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The smallest application that can be secured, so the chain can be asked what it actually enforces.
 * <p>
 * Three endpoints standing in for the shapes a CQRS application has - a command path, a view path and
 * something that is neither - plus the actuator, which the starter brings.
 * <p>
 * Deliberately contains <b>no</b> security beans. Anything a test needs beyond the real
 * auto-configuration is a {@code @TestConfiguration} it imports by name - see
 * {@link StubJwtDecoderConfiguration} for what happens otherwise.
 */
@SpringBootApplication
public class SecurityTestApplication {

    /** Endpoints standing in for a real application's. */
    @RestController
    static class Endpoints {

        @GetMapping("/cmd/anything")
        String command() {
            return "command";
        }

        @GetMapping("/view/anything")
        String view() {
            return "view";
        }

        @GetMapping("/something-else")
        String other() {
            return "other";
        }
    }

}
