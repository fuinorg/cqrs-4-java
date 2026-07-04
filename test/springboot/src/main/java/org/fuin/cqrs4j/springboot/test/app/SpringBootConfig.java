package org.fuin.cqrs4j.springboot.test.app;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBProjectionManagementClient;
import org.fuin.cqrs4j.jackson.Cqrs4JacksonModule;
import org.fuin.cqrs4j.springboot.query.core.base.EventstoreConfig;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.esc.api.ConverterRegistry;
import org.fuin.esc.api.EnhancedMimeType;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.SerDeserializerRegistry;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.esc.api.Serializer;
import org.fuin.esc.api.SimpleSerializerDeserializerRegistry;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.esc.api.TenantContext;
import org.fuin.esc.api.UpcastingDeserializerRegistry;
import org.fuin.esc.client.JandexSerializedDataTypeRegistry;
import org.fuin.esc.esgrpc.ESGrpcEventStore;
import org.fuin.esc.esgrpc.ESGrpcEventStoreAsync;
import org.fuin.esc.esgrpc.GrpcProjectionAdminEventStore;
import org.fuin.esc.esgrpc.IESGrpcEventStore;
import org.fuin.esc.jackson.BaseTypeFactory;
import org.fuin.esc.jackson.EscJacksonModule;
import org.fuin.esc.jackson.EscJacksonUtils;
import org.fuin.esc.jackson.JacksonSerDeserializer;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.jackson.ImmutableObjectMapper;
import org.fuin.objects4j.jackson.Objects4JJacksonModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@ThreadSafe
@Configuration
public class SpringBootConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBootConfig.class);

    @Bean
    public EntityIdFactory entityIdFactory() {
        return new JandexEntityIdFactory();
    }

    @Bean
    public ImmutableObjectMapper.Builder immutableObjectMapperBuilder(
            EntityIdFactory entityIdFactory) {
        return new ImmutableObjectMapper.Builder(new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .registerModule(new Cqrs4JacksonModule())
                .registerModule(new Objects4JJacksonModule())
                .registerModule(new Ddd4JacksonModule(entityIdFactory))
                .registerModule(new TestModelJacksonModule(entityIdFactory))
        );
    }

    @Bean
    public ImmutableObjectMapper.Provider immutableObjectMapperProvider(
            ImmutableObjectMapper.Builder mapperBuilder) {
        return new ImmutableObjectMapper.Provider(mapperBuilder);
    }

    @Bean
    public JacksonSerDeserializer jacksonSerDeserializer(
            final ImmutableObjectMapper.Provider mapperProvider,
            final SerializedDataTypeRegistry typeRegistry) {
        return new JacksonSerDeserializer.Builder()
                .withObjectMapper(mapperProvider)
                .withTypeRegistry(typeRegistry)
                .withEncoding(StandardCharsets.UTF_8)
                .build();
    }


    @Bean
    public SerializedDataTypeRegistry serializedDataTypeRegistry() {
        return new JandexSerializedDataTypeRegistry();
    }

    @Bean
    public SerDeserializerRegistry serDeserializerRegistry(SerializedDataTypeRegistry typeRegistry,
                                                           JacksonSerDeserializer jacksonSerDeserializer,
                                                           ImmutableObjectMapper.Builder mapperBuilder) {

        final SimpleSerializerDeserializerRegistry.Builder builder = new SimpleSerializerDeserializerRegistry.Builder(EscJacksonUtils.MIME_TYPE);
        for (final SerializedDataTypeRegistry.TypeClass tc : typeRegistry.findAll()) {
            builder.add(tc.type(), jacksonSerDeserializer);
            LOG.info("Registered type '{}' with serializer: {}", tc.type().asBaseType(), jacksonSerDeserializer.getClass().getSimpleName());
        }
        final SerDeserializerRegistry registry = builder.build();
        mapperBuilder.registerModule(new EscJacksonModule(registry, registry));
        return registry;
    }

    /**
     * Command serializer used by the outbox to marshal a command and stamp its content type. This app serializes
     * commands as JSON via Jackson (no per-command registration needed); an XML application would supply an XML
     * serializer here instead, and the content type would follow.
     *
     * @param objectMapper Jackson mapper.
     * @return JSON command serializer.
     */
    @Bean
    public Serializer commandSerializer(final ObjectMapper objectMapper) {
        final EnhancedMimeType mimeType = EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8);
        return new Serializer() {
            @Override
            public EnhancedMimeType getMimeType() {
                return mimeType;
            }

            @Override
            public <T> byte[] marshal(final T obj, final SerializedDataType type) {
                try {
                    return objectMapper.writeValueAsBytes(obj);
                } catch (final JsonProcessingException ex) {
                    throw new IllegalStateException("Failed to serialize command: " + type, ex);
                }
            }
        };
    }

    @Bean(destroyMethod = "shutdown")
    public KurrentDBClient createKurrentDBClient(final EventstoreConfig config) {
        final KurrentDBClientSettings settings = KurrentDBClientSettings.builder()
                .addHost(config.getHost(), config.getPort())
                .defaultCredentials("admin", "changeit") // Just for test
                .tls(config.isTls())
                .buildConnectionSettings();
        return KurrentDBClient.create(settings);
    }

    @Bean
    public KurrentDBProjectionManagementClient createKurrentDBProjectionManagementClient(final EventstoreConfig config) {
        final KurrentDBClientSettings settings = KurrentDBClientSettings.builder()
                .addHost(config.getHost(), config.getPort())
                .defaultCredentials("admin", "changeit") // Just for test
                .tls(config.isTls())
                .buildConnectionSettings();
        return KurrentDBProjectionManagementClient.create(settings);
    }


    /**
     * Creates an event store connection.
     *
     * @param client Client to use.
     * @return New event store instance.
     */
    @SuppressWarnings("java:S2095") // Spring will correctly close it by calling "close()" on instance
    @Bean(destroyMethod = "close")
    public IESGrpcEventStore getESGrpcEventStore(final SerDeserializerRegistry registry,
                                                 final ConverterRegistry converterRegistry,
                                                 final KurrentDBClient client) {
        return new ESGrpcEventStore.Builder()
                .eventStore(client)
                .serRegistry(registry)
                .desRegistry(new UpcastingDeserializerRegistry(registry, converterRegistry))
                .baseTypeFactory(new BaseTypeFactory())
                .targetContentType(EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .build()
                .open();
    }

    /**
     * Creates an asynchronous, subscribable GRPC event store used by the push-based projection mode. It reuses
     * the same {@link KurrentDBClient} as the synchronous store (the client is owned externally, so this store's
     * {@code open()}/{@code close()} are no-ops).
     *
     * @param registry          Serializer/deserializer registry.
     * @param converterRegistry Up-caster registry decorating deserialization.
     * @param client            Shared KurrentDB client.
     * @return New subscribable async event store instance.
     */
    @Bean
    public SubscribableEventStoreAsync getESGrpcEventStoreAsync(final SerDeserializerRegistry registry,
                                                               final ConverterRegistry converterRegistry,
                                                               final KurrentDBClient client) {
        return new ESGrpcEventStoreAsync.Builder()
                .eventStore(client)
                .serRegistry(registry)
                .desRegistry(new UpcastingDeserializerRegistry(registry, converterRegistry))
                .baseTypeFactory(new BaseTypeFactory())
                .targetContentType(EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Creates an GRPC based projection admin event store.
     *
     * @param client Client to use.
     * @param tenantContext Optional tenant context.
     * @return New event store instance.
     */
    @SuppressWarnings("java:S2095") // Spring will correctly close it by calling "close()" on instance
    @Bean(destroyMethod = "close")
    public ProjectionAdminEventStore getProjectionAdminEventStore(final KurrentDBProjectionManagementClient client,
                                                                  final Optional<TenantContext> tenantContext) {
        return new GrpcProjectionAdminEventStore(client, null).open();
    }

    @Bean
    public HttpClient getHttpClient(final EventstoreConfig config) {
        return HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        // Just for test
                        return new PasswordAuthentication(
                                "admin",
                                "changeit".toCharArray());
                    }
                })
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .build();
    }

}
