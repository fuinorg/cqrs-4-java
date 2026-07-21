package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Command endpoint every application gets by depending on the command starter. It dispatches the
 * posted command and takes the caller from the {@link CommandExecutionContextProvider}, so no
 * application has to write this controller itself.
 * <p>
 * Unlike a hand-written variant it really does implement {@link CommandRestControllerApi}: there is
 * no extra {@code Authentication} parameter and no {@code throws Throwable}, both of which make an
 * override impossible.
 */
@ThreadSafe
@RestController
public class CommandRestController implements CommandRestControllerApi {

    private final CommandDispatcher dispatcher;

    private final CommandExecutionContextProvider contextProvider;

    /**
     * Constructor with all mandatory data.
     *
     * @param dispatcher Dispatcher the command is handed to.
     * @param contextProvider Provides the caller of the current request.
     */
    public CommandRestController(final CommandDispatcher dispatcher,
            final CommandExecutionContextProvider contextProvider) {
        this.dispatcher = dispatcher;
        this.contextProvider = contextProvider;
    }

    @Override
    @PostMapping("/cmd/{type}")
    public String cmd(final String type, final String cmdJson) {
        try {
            return dispatcher.dispatch(type, null, cmdJson, contextProvider.current(),
                    contextProvider.currentUserRoles());
        } catch (final CommandExecutionFailedException ex) {
            // The interface declares no checked exception, so the cause is wrapped instead of
            // widening the signature - a "throws Throwable" would make the override illegal.
            throw new CommandExecutionRuntimeException(ex);
        }
    }

}
