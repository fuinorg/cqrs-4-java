package org.fuin.cqrs4j.quarkus.test.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.fuin.cqrs4j.jsonb.JandexJsonbRegistry;
import org.fuin.cqrs4j.jsonb.JsonbRegistry;
import org.fuin.cqrs4j.quarkus.base.EventstoreConfig;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.esc.api.EnhancedMimeType;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.SerDeserializerRegistry;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.esc.api.SimpleSerializerDeserializerRegistry;
import org.fuin.esc.api.TenantContext;
import org.fuin.esc.client.JandexSerializedDataTypeRegistry;
import org.fuin.esc.esgrpc.ESGrpcEventStore;
import org.fuin.esc.esgrpc.GrpcProjectionAdminEventStore;
import org.fuin.esc.esgrpc.IESGrpcEventStore;
import org.fuin.esc.jsonb.BaseTypeFactory;
import org.fuin.esc.jsonb.EscJsonbUtils;
import org.fuin.esc.jsonb.JsonbSerDeserializer;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.jsonb.FieldAccessStrategy;
import org.fuin.objects4j.jsonb.JsonbProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * CDI factory that creates necessary beans.
 */
@ThreadSafe
@ApplicationScoped
public class QuarkusFactory {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusFactory.class);

    @Produces
    @Singleton
    public EntityIdFactory entityIdFactory() {
        return new JandexEntityIdFactory();
    }

    @Produces
    @Singleton
    public JsonbConfig jsonbConfig() {
        return new JsonbConfig()
                .withPropertyVisibilityStrategy(new FieldAccessStrategy())
                .withEncoding(StandardCharsets.UTF_8.name());
    }

    @Produces
    @Singleton
    public JsonbProvider jsonbProvider(JsonbConfig jsonbConfig) {
        return new JsonbProvider(jsonbConfig);
    }

    @Produces
    @Singleton
    public JsonbSerDeserializer jsonbSerDeserializer(JsonbProvider jsonbProvider,
                                                     SerializedDataTypeRegistry typeRegistry) {
        return new JsonbSerDeserializer(jsonbProvider, typeRegistry, StandardCharsets.UTF_8);
    }

    @Produces
    @Singleton
    public SerializedDataTypeRegistry serializedDataTypeRegistry() {
        return new JandexSerializedDataTypeRegistry();
    }

    @Produces
    @Singleton
    public SerDeserializerRegistry serDeserializerRegistry(JsonbConfig jsonbConfig,
                                                           JsonbProvider jsonbProvider,
                                                           EntityIdFactory entityIdFactory,
                                                           SerializedDataTypeRegistry typeRegistry,
                                                           JsonbSerDeserializer jsonbSerDeserializer) {

        final SimpleSerializerDeserializerRegistry.Builder builder = new SimpleSerializerDeserializerRegistry.Builder(EscJsonbUtils.MIME_TYPE);
        for (final SerializedDataTypeRegistry.TypeClass tc : typeRegistry.findAll()) {
            builder.add(tc.type(), jsonbSerDeserializer);
            LOG.info("Registered type '{}' with serializer: {}", tc.type().asBaseType(), jsonbSerDeserializer.getClass().getSimpleName());
        }

        final SerDeserializerRegistry registry = builder.build();

        EscJsonbUtils.addEscSerDeserializer(builder, jsonbSerDeserializer);

        final JsonbRegistry jsonbRegistry = new JandexJsonbRegistry(entityIdFactory, registry, registry, jsonbProvider);
        jsonbConfig.withAdapters(jsonbRegistry.getAdapters().toArray(new JsonbAdapter[0]));
        jsonbConfig.withSerializers(jsonbRegistry.getSerializers().toArray(new JsonbSerializer[0]));
        jsonbConfig.withDeserializers(jsonbRegistry.getDeserializers().toArray(new JsonbDeserializer[0]));

        return registry;

    }

    @Produces
    @Singleton
    public KurrentDBWrapper kurrentDBWrapper(final EventstoreConfig config) {
        return new KurrentDBWrapper(config);
    }

    /**
     * Creates an GRPC based event store.<br>
     * <br>
     * CAUTION: The returned event store instance is NOT thread safe.
     *
     * @param kurrentDBWrapper Shared client connection.
     * @param registry         Serialization registry.
     * @return Application scope event store.
     */
    @Produces
    @Dependent
    public IESGrpcEventStore createEventStore(final KurrentDBWrapper kurrentDBWrapper,
                                              final SerDeserializerRegistry registry) {

        final IESGrpcEventStore eventstore = new ESGrpcEventStore.Builder()
                .eventStore(kurrentDBWrapper.getClient())
                .serDesRegistry(registry)
                .baseTypeFactory(new BaseTypeFactory())
                .targetContentType(EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .build();

        eventstore.open();
        return eventstore;

    }

    @Produces
    @Dependent
    @SuppressWarnings("java:S2095") // Resource will be closed with "disposes" method
    public ProjectionAdminEventStore getProjectionAdminEventStore(final KurrentDBWrapper kurrentDBWrapper,
                                                                  final Instance<TenantContext> tenantContext) {
        return new GrpcProjectionAdminEventStore(kurrentDBWrapper.getProjectionManagementClient(),
                tenantContext.isUnsatisfied() ? null : tenantContext.get()).open();

    }

    /**
     * Shuts the wrapper with the clients inside down when the context is disposed.
     *
     * @param kurrentDBWrapper Wrapper to shut down.
     */
    public void shutdownKurrentDBWrapper(@Disposes final KurrentDBWrapper kurrentDBWrapper) {
        kurrentDBWrapper.shutdown();
    }

    /**
     * Closes the GRPC based event store when the context is disposed.
     *
     * @param es Event store to close.
     */
    public void closeEventStore(@Disposes final IESGrpcEventStore es) {
        es.close();
    }

    /**
     * Closes the projection admin event store when the context is disposed.
     *
     * @param es Event store to close.
     */
    public void closeProjectionAdminEventStore(@Disposes final ProjectionAdminEventStore es) {
        es.close();
    }

}
