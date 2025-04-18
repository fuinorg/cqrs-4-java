package org.fuin.cqrs4j.springboot.test;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBProjectionManagementClient;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.jsonb.JandexJsonbRegistry;
import org.fuin.cqrs4j.jsonb.JsonbRegistry;
import org.fuin.cqrs4j.springboot.base.EventstoreConfig;
import org.fuin.cqrs4j.test.model.AbstractPersonsView;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.esc.api.EnhancedMimeType;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.SerDeserializerRegistry;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.esc.api.SimpleSerializerDeserializerRegistry;
import org.fuin.esc.client.JandexSerializedDataTypeRegistry;
import org.fuin.esc.esgrpc.ESGrpcEventStore;
import org.fuin.esc.esgrpc.GrpcProjectionAdminEventStore;
import org.fuin.esc.esgrpc.IESGrpcEventStore;
import org.fuin.esc.jsonb.BaseTypeFactory;
import org.fuin.esc.jsonb.JsonbDeSerializer;
import org.fuin.objects4j.jsonb.FieldAccessStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class SpringBootConfig {

    private static final String APPLICATION_JSON = "application/json";

    @Bean
    public PersonsView personsView(EntityManager em) {
        return new PersonsView(em);
    }

    @Bean
    public EntityIdFactory entityIdFactory() {
        return new JandexEntityIdFactory();
    }

    @Bean
    public JsonbRegistry jsonbRegistry(EntityIdFactory entityIdFactory) {
        return new JandexJsonbRegistry(entityIdFactory);
    }

    @Bean
    public JsonbDeSerializer jsonbDeSerializer(JsonbRegistry jsonbRegistry) {
        return JsonbDeSerializer.builder()
                .withAdapters(jsonbRegistry.getAdapters().toArray(new JsonbAdapter[0]))
                .withSerializers(jsonbRegistry.getSerializers().toArray(new JsonbSerializer[0]))
                .withDeserializers(jsonbRegistry.getDeserializers().toArray(new JsonbDeserializer[0]))
                .withPropertyVisibilityStrategy(new FieldAccessStrategy())
                .withEncoding(StandardCharsets.UTF_8).build();
    }

    @Bean
    public Jsonb jsonb(JsonbRegistry jsonbRegistry) {
        final JsonbConfig config = new JsonbConfig()
                .withAdapters(jsonbRegistry.getAdapters().toArray(new JsonbAdapter[0]))
                .withSerializers(jsonbRegistry.getSerializers().toArray(new JsonbSerializer[0]))
                .withDeserializers(jsonbRegistry.getDeserializers().toArray(new JsonbDeserializer[0]))
                .withPropertyVisibilityStrategy(new FieldAccessStrategy());
        return JsonbBuilder.create(config);
    }

    @Bean
    public SerializedDataTypeRegistry serializedDataTypeRegistry() {
        return new JandexSerializedDataTypeRegistry();
    }

    @Bean
    public SerDeserializerRegistry serDeserializerRegistry(SerializedDataTypeRegistry typeRegistry,
                                                           JsonbDeSerializer jsonbDeSerializer) {

        final SimpleSerializerDeserializerRegistry registry = new SimpleSerializerDeserializerRegistry();
        for (final SerializedDataTypeRegistry.TypeClass tc : typeRegistry.findAll()) {
            registry.add(tc.type(), APPLICATION_JSON, jsonbDeSerializer);
        }
        // Required to solve cyclic dependency on each other
        jsonbDeSerializer.init(typeRegistry, registry, registry);
        return registry;

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
                                                 final KurrentDBClient client) {
        return new ESGrpcEventStore.Builder()
                .eventStore(client)
                .serDesRegistry(registry)
                .baseTypeFactory(new BaseTypeFactory())
                .targetContentType(EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .build()
                .open();
    }

    /**
     * Creates an GRPC based projection admin event store.
     *
     * @param client Client to use.
     * @return New event store instance.
     */
    @SuppressWarnings("java:S2095") // Spring will correctly close it by calling "close()" on instance
    @Bean(destroyMethod = "close")
    public ProjectionAdminEventStore getProjectionAdminEventStore(final KurrentDBProjectionManagementClient client) {
        return new GrpcProjectionAdminEventStore(client).open();
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
