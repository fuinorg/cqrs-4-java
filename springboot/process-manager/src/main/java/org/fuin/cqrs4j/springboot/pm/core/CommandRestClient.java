package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Generic HTTP client for sending a serialized command to a command endpoint. Mirrors the generic
 * command controller contract ({@code POST /cmd/{type}} with the JSON command as request body). An
 * implementation is created at runtime via {@code HttpServiceProxyFactory} over a {@code RestClient}
 * pointing at the configured base URL.
 * <p>
 * All implementations are expected to be thread safe.
 */
@ThreadSafe
public interface CommandRestClient {

    /**
     * Sends a command for execution.
     *
     * @param type        Unique type name of the command (path variable).
     * @param contentType {@code Content-Type} header, carrying the command's schema {@code version} parameter
     *                    (e.g. {@code application/json;version=2}) so the receiver can up-cast.
     * @param cmdJson     Serialized command (JSON request body).
     * @return Result returned by the command endpoint (JSON).
     */
    @PostExchange("/cmd/{type}")
    String cmd(@PathVariable("type") String type,
               @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
               @RequestBody String cmdJson);

}
