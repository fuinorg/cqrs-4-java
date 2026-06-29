package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.fuin.objects4j.common.ThreadSafe;

import java.net.http.HttpHeaders;

/**
 * Default {@link CommandAuthProvider} that adds no authentication headers. Used when the command
 * endpoint is internal / unsecured and no application-specific provider is configured.
 */
@ThreadSafe
public class NoOpCommandAuthProvider implements CommandAuthProvider {

    @Override
    public HttpHeaders create(final HttpHeaders headers) {
        // Intentionally returns the headers unchanged - no authentication.
        return headers;
    }

}
