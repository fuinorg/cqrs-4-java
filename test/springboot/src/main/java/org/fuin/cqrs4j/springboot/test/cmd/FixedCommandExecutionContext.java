package org.fuin.cqrs4j.springboot.test.cmd;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.User;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Fixed command execution context for the command-dedup demo. A real application derives this from the
 * authenticated request (e.g. a Keycloak token); the demo uses a constant system user and tenant.
 */
@ThreadSafe
public class FixedCommandExecutionContext implements CommandExecutionContext {

    private static final User SYSTEM_USER = new User() {
        @Override
        public String getUserId() {
            return "system";
        }

        @Override
        public String getUserName() {
            return "system";
        }
    };

    private static final TenantId DEFAULT_TENANT = new TenantId("default");

    @Override
    public TenantId getTenantId() {
        return DEFAULT_TENANT;
    }

    @Override
    public User getUser() {
        return SYSTEM_USER;
    }

}
