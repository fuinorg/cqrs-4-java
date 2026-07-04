package org.fuin.cqrs4j.quarkus.test.cmd;

import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandAuthorizer;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * Authorizer that permits every command. Kept intentionally trivial for the command-dedup demo, which is about
 * receiver-side deduplication rather than security.
 */
@ThreadSafe
public class PermitAllCommandAuthorizer implements CommandAuthorizer {

    @Override
    public Result authorized(final Command command, final List<SimpleRole> userRoles) {
        return new Result(true, command, List.of(), userRoles);
    }

}
