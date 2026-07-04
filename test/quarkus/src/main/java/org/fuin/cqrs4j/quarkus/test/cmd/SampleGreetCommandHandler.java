package org.fuin.cqrs4j.quarkus.test.cmd;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Handles the {@link SampleGreetCommand} by producing a greeting via the {@link GreetingRecorder}. The recorder
 * is the observable side effect used to prove that a re-delivered command is handled only once.
 */
@ThreadSafe
@ApplicationScoped
public class SampleGreetCommandHandler implements CommandHandler<SampleGreetCommand, String> {

    @Inject
    GreetingRecorder recorder;

    @Override
    public Class<SampleGreetCommand> getCommandType() {
        return SampleGreetCommand.class;
    }

    @Override
    public String handle(final CommandExecutionContext context, final SampleGreetCommand cmd) {
        return recorder.greet(cmd.getName());
    }

}
