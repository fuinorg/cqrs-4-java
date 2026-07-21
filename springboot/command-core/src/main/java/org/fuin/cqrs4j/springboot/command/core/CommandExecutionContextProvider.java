package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * Determines who is executing the command that is currently being handled.
 * <p>
 * This is the inbound counterpart of {@link org.fuin.cqrs4j.core.CommandAuthProvider}, which adds
 * authentication to an <em>outgoing</em> command. An implementation reads whatever the runtime knows
 * about the caller - for Spring Security the {@code Authentication} held by the
 * {@code SecurityContextHolder} - and turns it into a {@link CommandExecutionContext}.
 * <p>
 * Taking the caller from the security context instead of a controller method parameter is what
 * allows one {@code @HttpExchange} interface to serve both the REST client proxy and the server
 * implementation: a client proxy cannot carry a server-only {@code Authentication} argument.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface CommandExecutionContextProvider {

    /**
     * Returns the context of the caller currently being served.
     *
     * @return Execution context, never <code>null</code>.
     */
    CommandExecutionContext current();

    /**
     * Returns the roles the caller currently being served has.
     *
     * @return Roles, never <code>null</code> but possibly empty.
     */
    List<SimpleRole> currentUserRoles();

}
