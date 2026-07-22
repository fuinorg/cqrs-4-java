# Resilience Tasks — cqrs-4-java

Part of the [Resilience Roadmap](https://github.com/fuinorg/ddd-cqrs-4-java-example/blob/develop/resilience-roadmap.md). This is where most
**policy** lives, because this repo has the framework integration modules:

- **Quarkus modules** (`cqrs-4-java-quarkus-*`) → **SmallRye Fault Tolerance**
  (`quarkus-smallrye-fault-tolerance`; MP-FT annotations, MP-Config overrides).
- **Spring modules** (`cqrs-4-java-springboot-*`) → **Resilience4j**
  (`resilience4j-spring-boot3` + `spring-boot-starter-aop`; `resilience4j.*` config).
- **Neutral modules** (`core`, `esc`, `jpa`) → classification/util only, no FT framework.

Depends on **event-store-commons Phase 0** (`EscConnectionException`) and complements
**ddd-4-java** (connectivity-aware repository).

Legend: `[ ]` todo · **S1** event store · **S2** database · **S3** keycloak · **S4** command→query HTTP.
Framework tags: **[Q]** SmallRye FT (Quarkus) · **[S]** Resilience4j (Spring) · **[N]** neutral.

---

## Phase 0 — Foundation

### F1. Shared transient-failure classifier — **[N]**
- [x] Promoted to `CqrsUtils.isTransientInfrastructureFailure(Throwable)` in `cqrs-4-java-core`; both view
      managers delegate, and both circuit breaker predicates use it. Covered by `CqrsUtilsTest`.
      Keyed on `java.util.concurrent.TimeoutException` (the esc call timeout) + `io.grpc.*` + `java.net.*` +
      `java.io.IOException` + `java.sql.*` + `jakarta.persistence.*` + `org.springframework.dao.*`.
- [x] `EscConnectionException` is used now that esc Phase 0 shipped it. Kept layered so that
      `cqrs-4-java-core` stays free of any event store dependency (its ArchUnit rule enforces that):
      the new `EscUtils.isTransientInfrastructureFailure(..)` in `cqrs-4-java-esc` does the typed
      `instanceof` and delegates to `CqrsUtils` for everything else. Both view managers and both circuit
      breaker predicates use `EscUtils`.

### F2. Dependencies & config skeleton
- [x] **[Q]** `quarkus-query` builds against `smallrye-fault-tolerance-api` +
      `microprofile-fault-tolerance-api` (NOT the extension - `dependency:analyze` rejects it as unused,
      same arrangement as `quarkus-scheduler-api`). **The application must add
      `io.quarkus:quarkus-smallrye-fault-tolerance`** for the runtime SPI; `test/quarkus` does.
- [ ] **[Q]** Still missing in `quarkus-process-manager` / `quarkus-command`.
- [x] **[S]** `springboot-query-core` uses `resilience4j-circuitbreaker` + `resilience4j-core`
      (programmatic API). Versions added to `org.fuin:bom` - neither framework BOM manages resilience4j.
- [ ] **[S]** `resilience4j-spring-boot3` + `spring-boot-starter-aop` + actuator health/metrics NOT added:
      the guards are applied programmatically, so no AOP proxies are involved. Needed only if annotation
      driven config or actuator integration is wanted.
- [ ] **[S]** Still missing in `springboot-pm-core` / `keycloak-core`.
- [x] Config namespace `org.fuin.cqrs4j.projection.breaker.*` for **[Q]** (all with defaults).
      Documented in [resilience.md](resilience.md).
- [ ] **[S]** side is not configurable yet - the values are constants in `SpringViewManager`.

---

## Phase 1 — DONE (2026-07-22) — S4: Command→Query HTTP (outbox delivery)

O1 (Quarkus) and O2 (Spring) are implemented; O3 is obsolete (the example no longer has a command→query
HTTP call - see below). Both outboxes now have connect/request timeouts, typed transient vs. permanent
delivery failures, and a circuit breaker that **defers the batch without consuming the retry budget** while
the endpoint is down. Documented for users in [resilience.md](resilience.md).

**Still open across both:** no per-call retry/bulkhead (deliberate - the outbox is the retry mechanism),
no breaker metrics, and no actuator health indicator on the Spring side.

---

## Phase 1 — original tasks — S4: Command→Query HTTP (outbox delivery) — highest value

The outbox already gives **durable redelivery + dead-letter** (`CommandOutboxService.recordFailure` →
DLQ at `maxRetries`, default 5). Add fast-fail + backoff + timeout so a down query service doesn't burn
the whole batch each `*/5s` tick.

### O1. Quarkus outbox — **[Q] S4**
Files: `quarkus/process-manager/.../CommandRestClient.java` (JDK `HttpClient`, **no timeout**),
`.../CommandQueueExecutor.java` (`deliver`, `@Scheduled drain`), `.../CommandOutboxService.java`
(`recordFailure`), `.../CommandQueueConfig.java`.
- [x] **Prerequisite that was missing:** every failure (`IOException`, 4xx and 5xx alike) was collapsed
      into one `IllegalStateException`, so nothing could tell "endpoint down" from "command rejected" and
      no retry predicate could work. Added `CommandDeliveryException` (permanent - the endpoint answered
      and the command is the problem) and `TransientCommandDeliveryException` (unreachable / timed out /
      5xx) to `cqrs-4-java-core`; `CqrsUtils` checks the permanent case first, so an answered 4xx stays
      permanent even with an `IOException` in its cause chain.
- [x] Connect timeout + per-request timeout on the JDK `HttpClient`, both configurable
      (`.connectTimeout` / `.requestTimeout`, default 5s each).
- [x] `CircuitBreaker` around the delivery, applied **programmatically** (`Guard`) for the same reason as
      V1 - interceptors do not fire on self-invocation/private methods. Trips only on
      `CqrsUtils.isTransientInfrastructureFailure`, so a rejected command never opens it for everyone else.
      The application must add `io.quarkus:quarkus-smallrye-fault-tolerance` (the module builds against
      the APIs only), exactly like `quarkus-query`.
- [x] **DEVIATION from the task text.** The task said to *record the failure* on breaker-open. Implemented
      the opposite: on breaker-open **nothing is recorded** and the rest of the batch is deferred untouched.
      Recording would still consume the retry budget during an outage - with `maxRetries=5` and a 5s tick a
      25 second outage permanently dead-letters valid commands. A command that was never sent must not
      count as a failed attempt. A *rejected* command still records and still dead-letters at `maxRetries`.
      Both behaviours are pinned by tests.
- [x] Config keys under `org.fuin.cqrs4j.pm.cmdqueue.*`: `connectTimeout`, `requestTimeout`,
      `breaker.delay`, `breaker.requestVolumeThreshold`, `breaker.failureRatio`. **All with defaults** -
      a `@ConfigProperty` without `defaultValue` makes every existing application fail to start, which is
      exactly what happened during implementation and was only caught by `QuarkusAppTest`, not by the unit
      tests (they construct the config directly and never go through MP Config).
- [ ] No `@Retry` with backoff/jitter and no `@Bulkhead`. The scheduler's `tryLock` already prevents
      overlapping runs, and the outbox itself is the retry mechanism; per-delivery retry would multiply the
      time a wedged endpoint holds the drain thread.
- [ ] `CommandQueueExecutor.deliveryGuard` is package visible so tests can inject a pass-through: creating
      a real `Guard` needs the SmallRye FT runtime SPI, which does not exist outside the container
      (`NoClassDefFoundError: SpiAccess$Holder`). The same applies to `QuarkusViewManager`, where it is
      currently hidden by a `@Disabled` test.

### O2. Spring outbox — **[S] S4**
Files: `springboot/process-manager/.../CommandRestClient.java` (`@PostExchange`),
`.../ProcessManagerConfig.java` (`commandRestClient` bean — `RestClient` with **no timeout**),
`.../CommandQueueExecutor.java` (`deliver`, `@Scheduled drain`), `.../CommandQueueConfig.java`.
- [x] Connect + read timeouts on the `RestClient` via a `SimpleClientHttpRequestFactory` in
      `ProcessManagerConfig.commandRestClient` (default 5s each, configurable).
- [x] Status mapping at the client seam: `defaultStatusHandler` turns 5xx into
      `TransientCommandDeliveryException` and 4xx into `CommandDeliveryException`, so the typed
      distinction exists before anything reaches the executor. IO failures need no mapping - Spring's
      `ResourceAccessException` wraps `IOException`, which `CqrsUtils` already walks to.
- [x] Resilience4j `CircuitBreaker`, applied **programmatically** for the same reason as O1/V1 (Spring AOP
      does not intercept self-invocation or private methods). **Exponential open-state**
      (5s -> x2 -> max 5min) - unlike SmallRye FT on the Quarkus side, which only supports a fixed delay.
- [x] Same **DEVIATION** as O1: on breaker-open nothing is recorded and the rest of the batch is deferred
      untouched, instead of recording the failure. Pinned by a test.
- [x] Config under `org.fuin.cqrs4j.pm.cmdqueue.*`: `connectTimeout`, `requestTimeout`,
      `breaker.windowSize`, `breaker.failureRatePercent`, `breaker.initialWait`, `breaker.maxWait`.
      No startup trap here - Spring's `@ConfigurationProperties` constructor binding passes `null` for
      missing values and the constructor defaults them.
- [ ] No `@TimeLimiter` (it requires a `CompletableFuture` return type; the client timeout bounds the call
      instead), no `@Retry` and no `@Bulkhead` - the outbox itself is the retry mechanism and `tryLock`
      already prevents overlapping runs.
- [ ] No `resilience4j.*` instance config namespace: the breaker is configured through the
      `org.fuin.cqrs4j.pm.cmdqueue.breaker.*` keys instead, so there is one place to look regardless of
      framework. Adding `resilience4j-spring-boot3` would be needed for actuator health/metrics.

### O3. Example — command→query call (`cqrs-keycloak-example`, Spring) — **OBSOLETE (2026-07-22)**

**The scenario this task describes no longer exists in the example.** Checked against the current tree:

- `RemoteEntityRoleService` was **deleted** in `5af9883` ("Removed old example classes - Start with fresh
  generated ones"). There is nothing left to annotate.
- The timeouts are **already set**: `SharedExampleUtils.clientHttpRequestFactory()` uses connect 2s /
  read 5s.
- `SharedExampleUtils` is the only file in the repo that touches `RestClient`, and **nothing calls
  `createRestClient`** - it is currently dead code. The example does not use the process manager outbox
  either (no `cmdqueue` configuration).

So there is no command→query HTTP call left to guard. Deliberately **not** guarding `createRestClient`
speculatively: with no callers nothing would be exercised, tested or demonstrated.

**Revisit when the example regains a command→query call.** The task text above is still the right recipe:
timeouts at the `createRestClient` seam, then retry + circuit breaker on the calling service, 404 treated
as business (no retry) and 5xx/IO as transient, with a fallback to empty/last-known roles so a down query
service degrades the command instead of returning 500.

---

## Phase 2 — S1: Event store (dispatch + projection reads)

### V1. Projection catch-up hardening — **[Q]/[S] S1/S2**
Files: `quarkus/query/.../QuarkusViewManager.java`, `springboot/query-core/.../SpringViewManager.java`
(`prepareRead`, `readStreamEvents` → `readAllEventsForward` → `handleChunk`).
- [x] `prepareRead` (create projection + read checkpoint) **and** `readAllEventsForward`/`handleChunk`
      (reading and dispatching the events) are guarded by one shared circuit breaker in both
      frameworks, so a wedged store no longer blocks a thread on every tick. The guards are applied
      **programmatically** (**[Q]** `Guard`, **[S]** `CircuitBreaker.executeCallable`) rather than by
      annotation, because MP-FT / Spring AOP interceptors never fire on self-invocation or private methods -
      annotating `prepareRead` would have compiled and silently done nothing. Both breakers trip only on
      `CqrsUtils.isTransientInfrastructureFailure`. The schedule remains the ultimate self-heal.
      **[Q]** fixed open-state delay (SmallRye FT supports no more), **[S]** exponential 5s -> x2 -> max 5min.
      A transient failure during the read is now logged at DEBUG instead of ERROR on every tick; a view
      handler throwing is still an ERROR.
- [ ] No `@Timeout` layer: the operation bound comes from the esc call timeout (5s default) instead.
- [ ] Breakers expose no metrics yet; **[Q]** logs state changes at INFO, **[S]** logs nothing.
- [ ] Push-mode reconnect: `esc` `ViewSubscriptions` re-subscribes at a fixed `RESUBSCRIBE_BACKOFF_MILLIS
      = 5000`. Generalize to **exponential backoff + jitter + max-attempts** (coordinate with
      event-store-commons E1).

### V2. Command dispatch / dedup store — **[Q]/[S] S1/S2**
Files: `quarkus/command/.../QuarkusCommandDispatcher.java` + `QuarkusProcessedCommandStore.java`;
`springboot/command-core/.../CommandDispatcher.java` + `QryProcessedCommandStore.java`;
`core/.../ProcessedCommandStore.java`.
- [ ] The receiver's DB dedup (`processed`/`markProcessed`) has no local resilience; it's protected by the
      *sender's* outbox retry. Add an inbound **`@Bulkhead`** (and optional `@RateLimit`) so a slow DB or a
      redelivery storm sheds load / fails fast rather than exhausting request threads. Don't add retry here
      (the sender owns retry) — just isolation + fast-fail.

---

## Phase 3 — S2: Database (leases, positions, read model)

### DB1. Lease & position repos — **[Q]/[S] S2**
Files: `quarkus/query/.../QryProjectionLeaseService.java` + `QryProjectionPositionRepository.java`;
`springboot/query-core/.../QryProjectionLeaseService.java`; JPA entities in `jpa/query`.
- [ ] Set query/lock timeouts on the `@Transactional(REQUIRES_NEW)` pessimistic-lock `acquire`
      (avoid unbounded lock waits under HA contention). Map transient DB failures to the shared classifier;
      let the view-manager catch-up self-heal.
- [ ] Size a **[S]** HikariCP / **[Q]** Agroal pool + a bulkhead so projection DB work can't starve the
      request path.

---

## Phase 4 — S3: Keycloak

### K1. Spring keycloak — **[S] S3**
Files: `springboot/keycloak-core/.../JwtUtils.java` (`getConfiguration`, RestTemplate 30 s timeout),
`.../JwtTenant.java` (`getJWSKeySelector` — Nimbus JWKS fetch, **no timeout**),
`.../KeycloakTenantRepository.java` + `JwtTenantKeySelector.java` (per-issuer `ConcurrentHashMap` caches,
failed `computeIfAbsent` **not** negatively cached → retries every request).
- [ ] Add a connect/read timeout to the Nimbus JWKS fetch (`ResourceRetriever`/`DefaultResourceRetriever`).
- [ ] Guard `JwtUtils.getConfiguration` and the JWKS retrieval with Resilience4j `@Retry` + `@TimeLimiter`
      + `@CircuitBreaker`.
- [ ] **Negative cache with backoff**: a failed OIDC-discovery/JWKS lookup currently isn't cached, so a
      down Keycloak is hammered on every request per new issuer. Cache failures briefly (backoff) and keep
      a **last-known-good JWKS** as fallback so in-flight tokens keep validating during a short Keycloak
      outage.

### K2. Quarkus keycloak — **[Q] S3 (config, not FT)**
Files: `quarkus/keycloak/.../KeycloakTenantConfigResolver.java`, `KeycloakTenantRepository.java`.
- [ ] The app doesn't fetch JWKS itself — `quarkus-oidc` does. Tune OIDC resilience via config, not FT:
      `quarkus.oidc.connection-delay`, `connection-retry-count`, `connection-timeout`, and JWKS cache
      settings. Document the recommended values; no annotations needed.

---

## Phase 6 — Observability & fault-injection tests
- [ ] Emit metrics for every breaker/retry/bulkhead (Micrometer is already a dep in the process-manager
      modules): retry counts, CB state transitions, bulkhead rejections, timeouts. Expose **[S]** actuator
      health / **[Q]** SmallRye health.
- [ ] ITs (Testcontainers): `pause()`/`stop()` the eventstore, Keycloak, or the query service mid-test
      (or Toxiproxy) and assert graceful degradation + recovery for O1/O2/O3, V1, K1. Reuse the
      `test/quarkus` + `test/springboot` harnesses and the example ITs.

---

## Summary — module × framework × pattern

| Module | Framework | Scenario | Patterns |
|--------|-----------|----------|----------|
| `quarkus-process-manager` | SmallRye FT | S4 | Timeout, Retry+Backoff, CircuitBreaker, Bulkhead, Fallback |
| `springboot-pm-core` | Resilience4j | S4 | Timeout, Retry+Backoff, CircuitBreaker, Bulkhead, Fallback |
| `springboot-keycloak-core` | Resilience4j | S3 | Timeout, Retry, CircuitBreaker, Fallback (last-known JWKS) + negative cache |
| `quarkus-keycloak` | quarkus-oidc config | S3 | connection-retry/timeout/JWKS-cache |
| `quarkus-query` / `springboot-query-core` | SmallRye FT / Resilience4j | S1, S2 | Timeout, CircuitBreaker, Bulkhead; backoff on push-reconnect |
| `quarkus-command` / `springboot-command-core` | SmallRye FT / Resilience4j | S1, S2 | Bulkhead, RateLimit (inbound) |
| `core`, `esc`, `jpa` | neutral | all | shared classifier, timeouts, typed exceptions only |
