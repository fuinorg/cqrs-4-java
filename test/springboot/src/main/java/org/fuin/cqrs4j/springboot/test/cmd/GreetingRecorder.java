package org.fuin.cqrs4j.springboot.test.cmd;

import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Observable side effect for the command-dedup demo: counts how often a greeting was actually produced. If the
 * receiver-side deduplication works, a command re-delivered twice increments the counter only once.
 */
@ThreadSafe
@Component
public class GreetingRecorder {

    private final AtomicInteger count = new AtomicInteger();

    private volatile String lastGreeting = "";

    /**
     * Records a greeting for the given name.
     *
     * @param name Name that was greeted.
     * @return The produced greeting.
     */
    public String greet(final String name) {
        count.incrementAndGet();
        final String greeting = "Hello, " + name;
        lastGreeting = greeting;
        return greeting;
    }

    /**
     * Returns the number of greetings produced so far.
     *
     * @return Greeting count.
     */
    public int count() {
        return count.get();
    }

    /**
     * Returns the most recently produced greeting.
     *
     * @return Last greeting (empty before the first greeting).
     */
    public String lastGreeting() {
        return lastGreeting;
    }

}
