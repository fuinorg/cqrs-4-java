package org.fuin.cqrs4j.core;

import org.fuin.objects4j.common.ThreadSafe;

import java.net.http.HttpHeaders;

/**
 * Adds authentication information to the HTTP headers used to deliver a command to the command
 * endpoint. Applications provide an implementation (for example adding a Bearer / service-account
 * token) to replace the default no-op provider.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface CommandAuthProvider {

    /**
     * Creates a new set of HTTP headers based on the given ones, adding the necessary authentication
     * information. The argument is not modified.
     *
     * @param headers Current headers (not modified).
     * @return New headers including authentication.
     */
    HttpHeaders create(HttpHeaders headers);

}
