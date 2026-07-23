package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.fuin.cqrs4j.core.CommandOverloadedException;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.utils4j.TestOmitted;

/**
 * Answers a shed command with <b>HTTP 503</b> instead of letting it become a 500.
 * <p>
 * The status is what makes load shedding safe end to end: the sender's outbox classifies a 5xx as a transient
 * delivery failure, so the command is deferred and redelivered rather than counted towards the dead-letter
 * budget. Using 503 rather than a plain 500 additionally tells an operator that the receiver turned the
 * command away on purpose, which is not a bug.
 */
@ThreadSafe
@TestOmitted("Building a JAX-RS Response needs a RuntimeDelegate, which only exists inside the container - the mapping is exercised by the test application")
@Provider
public class CommandOverloadedExceptionMapper implements ExceptionMapper<CommandOverloadedException> {

    @Override
    public Response toResponse(final CommandOverloadedException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(exception.getMessage())
                .type(jakarta.ws.rs.core.MediaType.TEXT_PLAIN)
                .build();
    }

}
