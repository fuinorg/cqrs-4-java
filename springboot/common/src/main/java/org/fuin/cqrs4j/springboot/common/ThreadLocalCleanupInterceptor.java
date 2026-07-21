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
package org.fuin.cqrs4j.springboot.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.core.KeyValueEL;
import org.fuin.objects4j.core.Validators;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Removes the state objects4j binds to the current thread after every request: the {@link KeyValueEL}
 * {@code ELProcessor} and the {@link Validators} {@code Validator}. Because request threads are pooled, they would
 * otherwise be retained after the last request and leak memory (and in case of the {@code ELProcessor} its beans would
 * bleed into a subsequent request handled by the same thread).
 */
@ThreadSafe
public class ThreadLocalCleanupInterceptor implements HandlerInterceptor {

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response,
                                final Object handler, @Nullable final Exception ex) {
        KeyValueEL.clear();
        Validators.clear();
    }

}
