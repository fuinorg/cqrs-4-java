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

import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers a {@link KeyValueELCleanupInterceptor} in servlet web applications so the per-thread
 * {@link org.fuin.objects4j.core.KeyValueEL} processor is cleared after every request.
 * <p>
 * This auto-configuration is activated via the {@code AutoConfiguration.imports} file of the command and query
 * starters. Only active in a servlet web application and when Spring MVC ({@link WebMvcConfigurer}) is on the
 * classpath; otherwise it is inert.
 */
@ThreadSafe
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class KeyValueELCleanupAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyValueELCleanupInterceptor keyValueELCleanupInterceptor() {
        return new KeyValueELCleanupInterceptor();
    }

    @Bean
    public WebMvcConfigurer keyValueELCleanupWebMvcConfigurer(final KeyValueELCleanupInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(final InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }

}
