# Release Notes

## 0.7.0
- Added `StubAuthServer` to `cqrs-4-java-test-helper`: an OpenID Connect provider (discovery, `/auth`, `/token`, `/userinfo`, `/logout`) as a small in-process HTTP server, for testing a client's login without a Keycloak container. It **verifies** the PKCE challenge rather than accepting it, so a client that dropped PKCE or sent a `plain` challenge fails against it, and it reports a refusal as a redirect back to the client so such a test goes red instead of waiting out a login timeout.
- `cqrs-4-java-test-helper` is now managed by the BOM, and every module has a `README.md` saying in one sentence what it is (mapped from the root `README.md`).
- The projection catch-up settings are configurable instead of hard coded: new `ProjectionConfig` (`org.fuin.cqrs4j.projection.*`) on the Spring side carries the circuit breaker values, the wake-up re-subscribe schedule and the lease lock timeout, and Quarkus gained the matching `org.fuin.cqrs4j.projection.resubscribe.*` / `lease-lock-timeout-ms` properties. Every property defaults to the value that was hard coded before.
- A failed Keycloak issuer discovery is now negatively cached with a growing delay (1 s to 30 s), so a down identity provider is no longer contacted again by every request carrying that issuer. Issuers that were already resolved keep validating tokens throughout an outage.
- **Bugfix** `JwtTenantKeySelector` performed OIDC discovery and the JWK set fetch inside `ConcurrentHashMap.computeIfAbsent`, which holds the bin lock during that network I/O and made concurrent requests queue up behind it.
- The Keycloak OIDC discovery call is bounded at 5 s instead of the JDK default of 30 s (still overridable via `sun.net.client.defaultConnectTimeout` / `defaultReadTimeout`).
- **Bugfix** `CqrsUtils.isTransientInfrastructureFailure` classified *every* `jakarta.persistence.*`, `java.sql.*` and `org.springframework.dao.*` failure as transient, including `OptimisticLockException`, `DataIntegrityViolationException` and `SQLIntegrityConstraintViolationException`. Those are answers about the data: they were logged at `DEBUG` as "will retry" and one view's constraint violation could open the shared projection circuit breaker for every other view. They are now classified as permanent.
- The projection lease acquisition bounds its pessimistic lock with `jakarta.persistence.lock.timeout` (3 s), so an instance that is not the leader no longer parks a thread on the lease row on every tick.
- Added an inbound bulkhead around the command deduplication lookup (`BulkheadProcessedCommandStore`, SmallRye FT on Quarkus / Resilience4j on Spring) so a slow database or a redelivery storm sheds load instead of exhausting request threads. Refused commands are reported as the new `CommandOverloadedException` and answered with HTTP 503, which the sender's outbox treats as transient. Recording a processed command is never refused, so shedding can never cause a double execution.
- Push-mode wake-up subscriptions re-subscribe with an exponential, capped and jittered `Backoff` (from event-store-commons) instead of a fixed 5 s interval, and the schedule now also covers the first subscribe so an application may start before the event store is reachable.
  The `ViewSubscriptions(store, scheduler, long)` constructor is deprecated in favour of `ViewSubscriptions(store, scheduler, Backoff)`; it keeps the old behaviour.
- The `Person*` model classes of the two test applications moved from `src-gen` to regular `src/main/java` and use `org.fuin.objects4j.common.Immutable` instead of `javax.annotation.concurrent.Immutable`.
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
