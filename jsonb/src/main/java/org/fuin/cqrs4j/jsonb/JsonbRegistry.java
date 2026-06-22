package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * Contains all known JSON-B adapters, serializers and deserializers.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface JsonbRegistry {

    /**
     * Returns a list of JSON-B adapter instances.
     *
     * @return Adapters.
     */
    public List<? extends JsonbAdapter<?, ?>> getAdapters();

    /**
     * Returns a list of JSON-B serializer instances.
     *
     * @return Serializers.
     */
    public List<? extends JsonbSerializer<?>> getSerializers();

    /**
     * Returns a list of JSON-B deserializer instances.
     *
     * @return Deserializers.
     */
    public List<? extends JsonbDeserializer<?>> getDeserializers();

}
