package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * REST contract of the command endpoint: one operation that accepts a serialized command of the
 * given type. Being a plain JAX-RS interface it is usable as a MicroProfile REST client and is
 * implemented by the server side {@link CommandRestResource}.
 * <p>
 * Neither the request headers nor the caller's identity are parameters. A client proxy cannot supply
 * a server-only {@code @Context HttpHeaders}, so the resource injects it as a field instead - that is
 * what lets one interface serve both sides.
 */
@ThreadSafe
@Path("/cmd")
public interface CommandRestResourceApi {

    /**
     * Executes the given command.
     *
     * @param type Type of the command, used to find its deserializer and handler.
     * @param cmdJson Serialized command.
     *
     * @return Result of the command execution.
     */
    @POST
    @Path("{type}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    String command(@PathParam("type") String type, String cmdJson);

}
