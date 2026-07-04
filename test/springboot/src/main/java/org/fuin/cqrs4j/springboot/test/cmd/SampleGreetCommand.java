package org.fuin.cqrs4j.springboot.test.cmd;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.fuin.cqrs4j.jackson.AbstractCommand;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;

import java.io.Serial;

/**
 * Sample command used to demonstrate effectively-once command receipt. It carries only a {@code name}
 * payload; the {@code event-id} inherited from {@link AbstractCommand} travels with the JSON and is the
 * deduplication key on the receiver side.
 */
@HasSerializedDataTypeConstant("SER_TYPE")
public class SampleGreetCommand extends AbstractCommand {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Unique type name (used as the {@code /cmd/{type}} path variable and the registry key). */
    public static final SerializedDataType SER_TYPE = new SerializedDataType("SampleGreetCommand");

    private static final EventType TYPE = new EventType("SampleGreetCommand");

    @JsonProperty("name")
    private String name;

    /**
     * JSON constructor.
     */
    public SampleGreetCommand() {
        super();
    }

    /**
     * Constructor with mandatory data.
     *
     * @param name Name to greet.
     */
    public SampleGreetCommand(final String name) {
        super();
        this.name = name;
    }

    /**
     * Returns the name to greet.
     *
     * @return Name.
     */
    public String getName() {
        return name;
    }

    @Override
    public EventType getEventType() {
        return TYPE;
    }

}
