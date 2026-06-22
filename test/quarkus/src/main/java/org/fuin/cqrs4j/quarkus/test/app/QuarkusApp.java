package org.fuin.cqrs4j.quarkus.test.app;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Represents the (custom) entry point, most likely used to the Quarkus application in the IDE.
 */
@ThreadSafe
@QuarkusMain
public class QuarkusApp {

    /**
     * Main method to start the app.
     *
     * @param args Arguments from the command line.
     */
    public static void main(String[] args) {
        Quarkus.run(args);
    }

}