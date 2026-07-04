package org.fuin.cqrs4j.springboot.pm.core;

import io.micrometer.core.instrument.MeterRegistry;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.utils4j.TestOmitted;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Wires the transactional-outbox process manager support. Applications {@code @Import} this
 * configuration to enable the command queue executor and the outbox/dead-letter persistence. It
 * scans this package for the {@link CommandOutboxService} and {@link CommandQueueExecutor} beans,
 * registers the outbox entities, enables scheduling for the executor, and provides a default command
 * client. Applications may supply their own {@link CommandAuthProvider} bean to add authentication
 * headers; if none (or more than one) is present a no-op provider is used.
 */
@TestOmitted("Covered by integration test")
@ThreadSafe
@Configuration
@EnableScheduling
@EnableConfigurationProperties({CommandQueueConfig.class, ProcessTimeoutConfig.class})
@ComponentScan("org.fuin.cqrs4j.springboot.pm.core")
@EntityScan("org.fuin.cqrs4j.jpa.pm")
public class ProcessManagerConfig {

    /**
     * Builds the HTTP client used to deliver commands to the configured command endpoint. Each request
     * is enriched with authentication headers from the application-provided {@link CommandAuthProvider}
     * (a no-op provider is used if none, or more than one, is registered).
     *
     * @param config        Configuration holding the command endpoint base URL.
     * @param authProviders Optional application-provided authentication providers.
     * @return Command client proxy.
     */
    @Bean
    @ConditionalOnMissingBean
    public CommandRestClient commandRestClient(final CommandQueueConfig config,
                                               final ObjectProvider<CommandAuthProvider> authProviders) {
        final CommandAuthProvider authProvider = authProviders.getIfUnique(NoOpCommandAuthProvider::new);
        final ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            final HttpHeaders springHeaders = request.getHeaders();
            final java.net.http.HttpHeaders updated = authProvider.create(
                    java.net.http.HttpHeaders.of(springHeaders, (name, value) -> true));
            updated.map().forEach(springHeaders::put);
            return execution.execute(request, body);
        };
        final RestClient restClient = RestClient.builder()
                .baseUrl(config.getUrl())
                .requestInterceptor(authInterceptor)
                .build();
        final HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(CommandRestClient.class);
    }

    /**
     * Registers the outbox metrics binder, but only when Micrometer is on the classpath. Applications that
     * do not use Micrometer are unaffected.
     */
    @ThreadSafe
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    public static class OutboxMetricsConfig {

        /**
         * Binds the outbox depth and dead-letter gauges.
         *
         * @param outboxService Service providing the counts.
         * @return Meter binder registered with the application's meter registries.
         */
        @Bean
        @ConditionalOnMissingBean
        public OutboxMetrics outboxMetrics(final CommandOutboxService outboxService) {
            return new OutboxMetrics(outboxService);
        }

        /**
         * Binds the pending and overdue process-timeout gauges.
         *
         * @param repository Repository providing the counts.
         * @return Meter binder registered with the application's meter registries.
         */
        @Bean
        @ConditionalOnMissingBean
        public ProcessTimeoutMetrics processTimeoutMetrics(final ProcessTimeoutRepository repository) {
            return new ProcessTimeoutMetrics(repository);
        }

    }

}
