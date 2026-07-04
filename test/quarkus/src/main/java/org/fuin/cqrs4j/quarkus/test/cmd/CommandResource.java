package org.fuin.cqrs4j.quarkus.test.cmd;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.quarkus.cmd.QuarkusCommandDispatcher;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * Generic command receiver: accepts a command as JSON at {@code POST /cmd/{type}} and forwards it to the
 * {@link QuarkusCommandDispatcher}. When the dispatcher is configured with a processed-command store, a
 * re-delivered command is deduplicated (effectively-once receipt). The execution context is fixed for the demo;
 * a real application would derive it from the authenticated request.
 */
@ThreadSafe
@Path("/cmd")
public class CommandResource {

    @Inject
    QuarkusCommandDispatcher dispatcher;

    @Inject
    CommandExecutionContext executionContext;

    /**
     * Receives a command and forwards it to the appropriate handler.
     *
     * @param type    Unique type name of the command (path variable).
     * @param headers Request headers; the {@code version} parameter of {@code Content-Type} (if any) selects the
     *                command schema version so the dispatcher can up-cast.
     * @param cmdJson Command JSON (request body).
     * @return Handler result as JSON.
     * @throws CommandExecutionFailedException Something went wrong during dispatching or execution.
     */
    @POST
    @Path("{type}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String command(@PathParam("type") final String type, @Context final HttpHeaders headers, final String cmdJson)
            throws CommandExecutionFailedException {
        final MediaType mediaType = headers.getMediaType();
        final String version = mediaType == null ? null : mediaType.getParameters().get("version");
        return dispatcher.dispatch(type, version, cmdJson, executionContext, List.of());
    }

}
