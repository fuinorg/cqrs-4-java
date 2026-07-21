package org.fuin.cqrs4j.quarkus.cmd;

import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * Determines the roles of the caller currently being served.
 * <p>
 * The {@link org.fuin.cqrs4j.core.CommandExecutionContext} itself is already a CDI bean an
 * application produces, so only the roles need a seam of their own. An application that uses
 * Keycloak (or any other identity provider) supplies an implementation; without one the command
 * endpoint passes no roles, which a command requiring roles will reject.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface CommandUserRolesProvider {

    /**
     * Returns the roles the caller currently being served has.
     *
     * @return Roles, never <code>null</code> but possibly empty.
     */
    List<SimpleRole> currentUserRoles();

}
