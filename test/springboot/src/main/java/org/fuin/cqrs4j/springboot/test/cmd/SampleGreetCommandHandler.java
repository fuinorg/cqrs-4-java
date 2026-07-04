package org.fuin.cqrs4j.springboot.test.cmd;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Handles the {@link SampleGreetCommand} by producing a greeting via the {@link GreetingRecorder}. The recorder
 * is the observable side effect used to prove that a re-delivered command is handled only once.
 */
@ThreadSafe
@Component
public class SampleGreetCommandHandler implements CommandHandler<SampleGreetCommand, String> {

    private final GreetingRecorder recorder;

    /**
     * Constructor with mandatory dependencies.
     *
     * @param recorder Records the produced greeting.
     */
    public SampleGreetCommandHandler(final GreetingRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder==null");
    }

    @Override
    public Class<SampleGreetCommand> getCommandType() {
        return SampleGreetCommand.class;
    }

    @Override
    public String handle(final CommandExecutionContext context, final SampleGreetCommand cmd) {
        return recorder.greet(cmd.getName());
    }

}
