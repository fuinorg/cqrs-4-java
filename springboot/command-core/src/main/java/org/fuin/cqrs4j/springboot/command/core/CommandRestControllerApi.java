package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/**
 * REST contract of the command endpoint: one operation that accepts a serialized command of the
 * given type. Being an {@code @HttpExchange} interface it is usable as an HTTP-interface client
 * proxy and is implemented by the server side {@link CommandRestController}.
 * <p>
 * The caller's identity is deliberately <em>not</em> a parameter. A client proxy cannot supply a
 * server-only {@code Authentication}, so the server obtains it from
 * {@link CommandExecutionContextProvider} instead - that is what lets one interface serve both
 * sides.
 */
@ThreadSafe
public interface CommandRestControllerApi {

    /**
     * Executes the given command.
     *
     * @param type Type of the command, used to find its deserializer and handler.
     * @param cmdJson Serialized command.
     *
     * @return Result of the command execution.
     */
    @PostExchange("/cmd/{type}")
    String cmd(@PathVariable("type") String type, @RequestBody String cmdJson);

}
