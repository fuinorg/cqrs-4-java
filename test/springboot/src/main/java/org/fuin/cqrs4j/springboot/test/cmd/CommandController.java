package org.fuin.cqrs4j.springboot.test.cmd;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.springboot.command.core.CommandDispatcher;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Generic command receiver: accepts a command as JSON at {@code POST /cmd/{type}} and forwards it to the
 * {@link CommandDispatcher}. When the dispatcher is configured with a processed-command store, a re-delivered
 * command is deduplicated (effectively-once receipt). The execution context is fixed for the demo; a real
 * application would derive it from the authenticated request.
 */
@ThreadSafe
@RestController
public class CommandController {

    private final CommandDispatcher dispatcher;

    private final CommandExecutionContext executionContext;

    /**
     * Constructor with mandatory dependencies.
     *
     * @param dispatcher       Dispatches the command to its handler.
     * @param executionContext Execution context passed to the handler.
     */
    public CommandController(final CommandDispatcher dispatcher, final CommandExecutionContext executionContext) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher==null");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext==null");
    }

    /**
     * Receives a command and forwards it to the appropriate handler.
     *
     * @param type        Unique type name of the command (path variable).
     * @param contentType {@code Content-Type} header; its {@code version} parameter (if any) selects the command
     *                    schema version so the dispatcher can up-cast.
     * @param cmdJson     Command JSON (request body).
     * @return Handler result as JSON.
     * @throws CommandExecutionFailedException Something went wrong during dispatching or execution.
     */
    @PostMapping(path = "/cmd/{type}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String command(@PathVariable("type") final String type,
                          @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) final String contentType,
                          @RequestBody final String cmdJson)
            throws CommandExecutionFailedException {
        final String version = contentType == null ? null : MediaType.parseMediaType(contentType).getParameter("version");
        return dispatcher.dispatch(type, version, cmdJson, executionContext, List.of());
    }

}
