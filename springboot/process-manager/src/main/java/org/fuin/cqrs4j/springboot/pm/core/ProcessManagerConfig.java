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
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.fuin.cqrs4j.core.CommandDeliveryException;
import org.fuin.cqrs4j.core.TransientCommandDeliveryException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
// The four annotated classes of that package are registered explicitly instead of being
// discovered: they are all injected by type, so their bean names are irrelevant, and an
// application no longer has to have this package inside its own component scan.
@Import({CommandOutboxService.class, ProcessTimeoutRepository.class, CommandQueueExecutor.class,
        ProcessTimeoutSweeper.class})
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
        // Without timeouts a delivery to an unreachable or wedged endpoint blocks the drain thread
        // indefinitely; the outbox then makes no progress at all.
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getConnectTimeout());
        requestFactory.setReadTimeout(config.getRequestTimeout());
        final RestClient restClient = RestClient.builder()
                .baseUrl(config.getUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(authInterceptor)
                // Distinguish "the endpoint cannot handle this right now" from "the command itself is
                // rejected". Without it every status collapses into one exception and the delivery cannot
                // tell an outage (retry) from a rejection (dead-letter).
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) ->
                        throwDeliveryFailure(response, true))
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) ->
                        throwDeliveryFailure(response, false))
                .build();
        final HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(CommandRestClient.class);
    }

    /**
     * Translates an error response into the matching typed delivery exception.
     *
     * @param response         Response received from the command endpoint.
     * @param transientFailure {@literal true} if delivering the command again may succeed.
     * @throws IOException Reading the response body failed.
     */
    private static void throwDeliveryFailure(final ClientHttpResponse response, final boolean transientFailure)
            throws IOException {
        final int status = response.getStatusCode().value();
        final String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        final String message = "Command delivery failed: " + status + " " + body;
        if (transientFailure) {
            throw new TransientCommandDeliveryException(message, status, null);
        }
        throw new CommandDeliveryException(message, status, null);
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
