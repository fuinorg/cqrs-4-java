# Resilience Tasks — cqrs-4-java

What is implemented, and what was deliberately left out, is described in [resilience.md](resilience.md).
Only open points are listed here.

Where policy lives:

- **Quarkus modules** (`cqrs-4-java-quarkus-*`) → SmallRye Fault Tolerance
- **Spring modules** (`cqrs-4-java-springboot-*`) → Resilience4j
- **Neutral modules** (`core`, `esc`, `jpa`) → classification and utilities only, no fault tolerance framework

---

## Next

- **End-to-end token validation during a Keycloak outage.** Prove that a token still validates through the
  security filter chain while the identity provider is unreachable. The repository-level behaviour — a failed
  discovery is not repeated, it is retried once the backoff elapsed, and an already-resolved issuer keeps
  working — is covered against a real Keycloak; this adds the filter chain on top.
  **Harness work comes first:** neither test application wires Keycloak at all, so this needs a Keycloak
  container plus OIDC security wiring in the Spring test application before the test itself can exist.

---

## Nice to have

- **Metrics and health for the circuit breakers and bulkheads.** Retry counts, breaker state transitions,
  bulkhead rejections and timeouts as Micrometer meters, plus Spring actuator health and SmallRye health.
  Do this together with [micrometer-tasks.md](micrometer-tasks.md): both touch the same view manager classes,
  and the meter naming and tagging conventions belong to that roadmap. Note that `quarkus-query` has no
  Micrometer dependency yet — a decision for that roadmap to make once, for all meters.

- **A Quarkus integration test that wires the inbound bulkhead in a real application.** It would close two
  gaps at once: demonstrate the wiring an application has to do itself, and exercise the HTTP 503 mapping,
  which currently has no test because building a JAX-RS `Response` needs a `RuntimeDelegate` that only exists
  inside the container. The Spring equivalent of that mapping is unit tested.

- **Four `@Disabled("TODO Implement!")` placeholder tests**, never implemented: `SpringViewManagerTest`,
  `QuarkusViewManagerTest`, `QryProjectionServiceTest` and `QryProjectionPositionRepositoryTest`. Both
  **view managers therefore have no unit tests at all** — their behaviour is only covered indirectly, by the
  integration tests. An empty class that never runs is easy to mistake for coverage; implement when that area
  is next touched, or delete the placeholders.
