package org.fuin.cqrs4j.springboot.test.app;

import org.fuin.cqrs4j.springboot.base.EventstoreConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Represents the (custom) entry point, most likely used to the Quarkus application in the IDE.
 */
@SpringBootApplication(scanBasePackages = {
        "org.fuin.cqrs4j.springboot.view",
        "org.fuin.cqrs4j.springboot.test.view",
        "org.fuin.cqrs4j.springboot.test.app"
})
@EnableConfigurationProperties(EventstoreConfig.class)
@EntityScan(basePackages = {
        "org.fuin.cqrs4j.springboot.view",
        "org.fuin.cqrs4j.springboot.test.view",
        "org.fuin.cqrs4j.springboot.test.model"
})
@EnableScheduling
public class SpringBootApp {

    /**
     * Main method to start the app.
     *
     * @param args Arguments from the command line.
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringBootApp.class, args);
    }

}