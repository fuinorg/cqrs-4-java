package org.fuin.cqrs4j.quarkus.cmd;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * Command endpoint every application gets by depending on this module: it accepts a command as
 * {@code POST /cmd/{type}} and forwards it to the {@link QuarkusCommandDispatcher}. When the
 * dispatcher is configured with a processed-command store, a re-delivered command is deduplicated
 * (effectively-once receipt).
 * <p>
 * Unlike a hand-written variant it implements {@link CommandRestResourceApi}: the request headers
 * are injected as a field rather than taken as a method parameter, so the very same interface can be
 * used as a MicroProfile REST client.
 */
@ThreadSafe
@ApplicationScoped
public class CommandRestResource implements CommandRestResourceApi {

    // Injected by the JAX-RS runtime, which NullAway cannot see (unlike @Inject).
    @Context
    @SuppressWarnings("NullAway.Init")
    HttpHeaders headers;

    @Inject
    QuarkusCommandDispatcher dispatcher;

    @Inject
    CommandExecutionContext executionContext;

    @Inject
    Instance<CommandUserRolesProvider> userRolesProvider;

    @Override
    public String command(final String type, final String cmdJson) {
        // The Content-Type is passed through unchanged so the dispatcher deserializes and up-casts by
        // the exact media type (base type, encoding and version), not just "some JSON".
        final String contentType = headers.getHeaderString(HttpHeaders.CONTENT_TYPE);
        try {
            return dispatcher.dispatch(type, contentType, cmdJson, executionContext, userRoles());
        } catch (final CommandExecutionFailedException ex) {
            // The interface declares no checked exception, so the cause is wrapped instead of
            // widening the signature - a client proxy has nothing to catch.
            throw new CommandExecutionRuntimeException(ex);
        }
    }

    private List<SimpleRole> userRoles() {
        if (userRolesProvider.isUnsatisfied()) {
            return List.of();
        }
        return userRolesProvider.get().currentUserRoles();
    }

}
