package org.fuin.cqrs4j.springboot.command.starter;

import org.fuin.cqrs4j.springboot.command.core.CommandDispatcher;
import org.fuin.cqrs4j.springboot.command.core.CommandExecutionContextProvider;
import org.fuin.cqrs4j.springboot.command.core.CommandRestController;
import org.fuin.cqrs4j.springboot.command.core.SecurityContextCommandExecutionContextProvider;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Contributes the generic command endpoint. An application gets {@code POST /cmd/{type}} by
 * depending on this starter and supplying a {@link CommandDispatcher}; it no longer writes the
 * controller itself.
 * <p>
 * The controller is conditional on that dispatcher, so an application that has no command handling
 * yet is unaffected.
 */
@ThreadSafe
@AutoConfiguration
public class CommandEndpointAutoConfiguration {

    /**
     * Creates the provider that derives the caller from Spring Security's context.
     *
     * @param tenant Tenant name reported for every request.
     * @param anonymousUserId User id reported for an unauthenticated request.
     *
     * @return Execution context provider.
     */
    @Bean
    @ConditionalOnMissingBean(CommandExecutionContextProvider.class)
    // Referenced by name: Spring Security is an optional dependency, so the class may be absent.
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    public CommandExecutionContextProvider commandExecutionContextProvider(
            @Value("${org.fuin.cqrs4j.command.tenant:default}") final String tenant,
            @Value("${org.fuin.cqrs4j.command.anonymous-user:anonymous}") final String anonymousUserId) {
        return new SecurityContextCommandExecutionContextProvider(new TenantId(tenant), anonymousUserId);
    }

    /**
     * Creates the command endpoint.
     *
     * @param dispatcher Dispatcher the posted command is handed to.
     * @param contextProvider Provides the caller of the current request.
     *
     * @return Command endpoint.
     */
    @Bean
    @ConditionalOnMissingBean(CommandRestController.class)
    @ConditionalOnBean(CommandDispatcher.class)
    public CommandRestController commandRestController(final CommandDispatcher dispatcher,
            final CommandExecutionContextProvider contextProvider) {
        return new CommandRestController(dispatcher, contextProvider);
    }

}
