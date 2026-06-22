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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Servlet filter that clears the tenant from the {@link WritableTenantContext} after every request.
 * This prevents a tenant from leaking into a subsequent request handled by the same (pooled) thread.
 */
@ThreadSafe
public class CleanupTenantContextFilter extends OncePerRequestFilter {

    private final WritableTenantContext tenantContext;

    /**
     * Constructor with the tenant context to clean up.
     *
     * @param tenantContext Tenant context that is cleared once the request has been processed.
     */
    public CleanupTenantContextFilter(WritableTenantContext tenantContext) {
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext==null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }

}
