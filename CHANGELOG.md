# Release Notes

## 0.7.0
- **Incompatible** Refactored view classes
  - Changed back from `JpaEventHandler` to [EventHandler](core/src/main/java/org/fuin/cqrs4j/core/EventHandler.java).
    This results in changes in several places.
  - Added new [ViewRegistry](core/src/main/java/org/fuin/cqrs4j/core/ViewRegistry.java) that holds information about available views
  - ([View](core/src/main/java/org/fuin/cqrs4j/core/View.java))s now need to implement a `getBeanName()` method to make it more explicit what it is used for.
  - Added tenant handling to views
- Added new [springboot-starter](springboot-starter) module for better autoconfiguration
- Use fuin.org BOM to align dependencies
- Added [JSpecify](https://jspecify.dev/) and [NullAway](https://github.com/uber/nullaway)
- Projection lag metrics and outbox metrics using Micrometer
- Distributed HA projection lease with lease integration test (Testcontainers and MariaDB)
- View subscriptions as alternative to polling by using event storecommon subscriptions
- Command deduplication: Avoid handling the same command multiple times
- Process-manager lifecycle hardening (no zombies)
- Surface read-model freshness to clients
- Aligned Quarkus with Spring Boot implementation
- Allow views listening to a category of events (like "an entity was created") instead of a list of dedicated event
- Added KeyValueEL thread local cleanup for Quarkus and Spring Boot.

## 0.6.0
- Added new Jackson module
