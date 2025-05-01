package org.fuin.cqrs4j.jsonb;

import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

import java.util.List;

/**
 * Contains all known JSON-B adapters, serializers and deserializers.
 */
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
