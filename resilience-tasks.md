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
- [x] **Push-mode reconnect generalized (2026-07-23).** `ViewSubscriptions` now takes an
      `org.fuin.esc.api.Backoff` (the type added by event-store-commons E1) instead of a fixed
      `RESUBSCRIBE_BACKOFF_MILLIS = 5000`: exponential, capped, jittered, with an optional attempt limit.
      Both view managers pass `Backoff.DEFAULT` (500 ms -> x2 -> 30 s cap, 50% jitter, no limit).
      The attempt counter is **per stream and reset on every successful subscribe**, so a later outage starts
      at the beginning of the schedule rather than continuing at the delay the previous one ended with -
      pinned by a test that fails if the reset is removed.
      The same schedule now also covers the **first** subscribe, which is what lets an application start
      before the store is reachable. Exhausting a configured attempt limit is logged at `ERROR` and accepted:
      losing a wake-up subscription costs latency, not correctness, because the poll keeps reading from the
      checkpoint.
      The old `(store, scheduler, long millis)` constructor is kept and deprecated; it builds an equivalent
      non-growing, unjittered `Backoff`, so existing callers keep their exact behaviour.
- [x] **Not** delegating to event-store-commons `ReconnectingSubscribableEventStore`, although E1 added it.
      That decorator deliberately does not retry the initial subscribe (which this consumer needs), and its
      main feature - resuming after the last delivered event - does not apply to a wake-up subscription,
      which follows new events only and has no position to resume from. The shared piece worth reusing was
      `Backoff`, and that is what is reused.
- [ ] The re-subscribe schedule is a constant in both view managers, not configurable. Belongs with the
      other **[S]**/**[Q]** projection config work in F2.

### V2. Command dispatch / dedup store — **[Q]/[S] S1/S2**
Files: `quarkus/command/.../QuarkusCommandDispatcher.java` + `QuarkusProcessedCommandStore.java`;
`springboot/command-core/.../CommandDispatcher.java` + `QryProcessedCommandStore.java`;
`core/.../ProcessedCommandStore.java`.
- [x] **Inbound bulkhead added (2026-07-23).** `BulkheadProcessedCommandStore` decorates the neutral
      `ProcessedCommandStore` in both frameworks - **[Q]** SmallRye FT `Guard.withBulkhead()`, **[S]**
      Resilience4j `Bulkhead` with a short `maxWait` that absorbs the ordinary burstiness of an outbox drain.
      Applied **programmatically** for the third time and the same reason as O1/O2/V1: MP-FT and Spring AOP
      interceptors never fire on a bean the application wires up itself, so `@Bulkhead` would have compiled
      and silently done nothing.
      No retry here, as the task says - the sender's outbox owns that.
- [x] **Only `processed(..)` is guarded, never `markProcessed(..)`.** This is the part that is easy to get
      wrong: `processed` runs *before* any handler does anything, so refusing it costs nothing and the
      command simply arrives again. `markProcessed` runs *after* the handler succeeded - refusing it would
      leave the command executed but unrecorded, and the next redelivery would **execute it a second time**.
      A bulkhead across the whole store would turn an overload into a duplicate side effect. Pinned by a test
      in both frameworks that fills the bulkhead and then records.
- [x] A shed command is reported as the new neutral `CommandOverloadedException` and mapped to **HTTP 503**
      (**[Q]** `CommandOverloadedExceptionMapper`, **[S]** `CommandOverloadedExceptionHandler`). The status
      is what makes shedding safe end to end: the sender's outbox classifies a 5xx as
      `TransientCommandDeliveryException`, so the command is deferred and redelivered instead of counting
      towards the dead-letter budget. A 4xx would permanently dead-letter a perfectly valid command that was
      merely turned away.
- [x] Closes part of **F2**: `quarkus-command` now builds against the fault tolerance APIs (the application
      still supplies `io.quarkus:quarkus-smallrye-fault-tolerance`), and `springboot-command-core` against
      `resilience4j-bulkhead` + `-core`. **`resilience4j-bulkhead` had to be added to `org.fuin:bom`**
      (1.0.2-SNAPSHOT, pinned at 2.4.0 like the other two) - that BOM has to be published before CI here is
      green.
- [ ] The limits are constructor arguments, not configuration, and no `@RateLimit` was added. Belongs with
      the other **[Q]**/**[S]** config work in F2.
- [ ] **[Q]** `CommandOverloadedExceptionMapper` is `@TestOmitted`: building a JAX-RS `Response` needs a
      `RuntimeDelegate`, which only exists inside the container. The **[S]** handler has a real test, and the
      Quarkus mapping should be covered by an IT in `test/quarkus` (see Phase 6).

---

## Phase 3 — S2: Database (leases, positions, read model)

### DB1. Lease & position repos — **[Q]/[S] S2**
Files: `quarkus/query/.../QryProjectionLeaseService.java` + `QryProjectionPositionRepository.java`;
`springboot/query-core/.../QryProjectionLeaseService.java`; JPA entities in `jpa/query`.
- [x] **Lock acquisition bounded (2026-07-23).** Both lease services pass
      `jakarta.persistence.lock.timeout` (3 s, `LOCK_TIMEOUT_MILLIS`) to the `PESSIMISTIC_WRITE`
      `em.find(..)`. Without it, every instance that is not the leader parks a thread on the row for as long
      as the leader holds it - on every tick, on every instance. Giving up quickly is the right answer here
      because *not* getting the lease is the normal outcome, not a failure: another instance is projecting
      and the next tick tries again. That Hibernate and PostgreSQL genuinely honour this hint was established
      by the event-store-commons `JpaFaultInjectionIT`, which measures that a blocked acquisition waits
      exactly the configured timeout. The mocked stubs in both lease service tests now pin the argument.
- [x] **Fixed a real misclassification in the shared classifier while checking the second half of this
      item.** `CqrsUtils.isTransientInfrastructureFailure` matched `jakarta.persistence.`, `java.sql.` and
      `org.springframework.dao.` by prefix, so it answered "transient" for every failure in those packages -
      including `OptimisticLockException`, `DataIntegrityViolationException`, `DuplicateKeyException` and
      `SQLIntegrityConstraintViolationException`. Those are answers *about the data*: they will be there
      again on the next attempt. Treating them as transient meant they were logged at `DEBUG` as "will retry"
      (the exact failure mode the class javadoc warns about) and, worse, **one view's constraint violation
      opened the shared projection circuit breaker for every other view**. A `PERMANENT_DATA_FAILURES` set is
      now checked before the prefixes, and the cause chain still decides - a conflict wrapped in a
      `RollbackException` is classified by the conflict, not by the wrapper. Covered by three new tests.
      This is the same distinction event-store-commons F4 makes when it deliberately leaves
      `OptimisticLockException` unmapped.
- [ ] Pool sizing is documented as guidance in [resilience.md](resilience.md) rather than implemented: the
      library cannot size an application's HikariCP/Agroal pool. **No cross-view bulkhead was added.**
      `tryLocked` already limits the catch-up to one pass per view, so the worst case is bounded by the
      number of views - a number the application knows when it sizes its pool. Capping it further would need
      its own configuration knob and would *delay* projections, which is a poor trade when the poll is
      already the safety net. Revisit with the **F2** config work if a deployment reports starvation.

---

## Phase 4 — S3: Keycloak

### K1. Spring keycloak — **[S] S3**
Files: `springboot/keycloak-core/.../JwtUtils.java` (`getConfiguration`, RestTemplate 30 s timeout),
`.../JwtTenant.java` (`getJWSKeySelector` — Nimbus JWKS fetch, **no timeout**),
`.../KeycloakTenantRepository.java` + `JwtTenantKeySelector.java` (per-issuer `ConcurrentHashMap` caches,
failed `computeIfAbsent` **not** negatively cached → retries every request).
- [x] **The premise of this bullet was wrong and is corrected (2026-07-23).** The JWKS fetch is *not*
      unbounded: `JWSAlgorithmFamilyJWSKeySelector.fromJWKSetURL(url)` builds a `RemoteJWKSet`, which
      defaults to **500 ms** connect and read (`RemoteJWKSet.DEFAULT_HTTP_*`, overridable through the
      `com.nimbusds.jose.jwk.source.RemoteJWKSet.defaultHttp*Timeout` system properties). If anything that is
      on the tight side for a cold provider. Nothing was changed there; the defaults are now documented in
      [resilience.md](resilience.md) so the next reader does not re-derive this.
- [x] **The call that really was too long is the OIDC discovery**, which this module's copy of
      `JwtUtils` bounded at the JDK default of **30 s** connect and read - on the request thread, the first
      time an issuer is seen. Thirty seconds times the request threads is an outage of the whole service, not
      just of authentication. Now 5 s, still overridable through the same `sun.net.client.*` properties.
- [x] **Negative cache with backoff added** in `KeycloakTenantRepository`: a failed discovery is remembered
      per issuer and rethrown immediately, with the delay doubling from 1 s to a 30 s cap. Without it a down
      Keycloak was contacted again by every single request carrying that issuer. Covered by five tests using
      an overridable clock and discovery seam (`protected now()` / `createTenant(..)`, the pattern the lease
      services already use); `JwtTenant` gained a package-visible constructor that takes already-resolved
      settings so a test needs no HTTP.
- [x] **Fixed network I/O inside `ConcurrentHashMap.computeIfAbsent`** in `JwtTenantKeySelector`. The
      mapping function performed OIDC discovery and a JWK set fetch while holding the bin lock, so concurrent
      requests whose issuer hashes to the same bin queued behind it - precisely when the provider is slow.
      The JDK also documents that the mapping function must not update the map, which resolving an issuer did
      indirectly through the tenant repository. Resolution now happens outside the lock followed by
      `putIfAbsent`; the worst case is that two requests resolve the same new issuer once.
- [x] **Last-known-good needs no extra machinery:** the negative cache deliberately covers only *discovery*.
      A tenant resolved once stays cached and keeps validating from the keys Nimbus holds, so an outage never
      invalidates issuers that were already working - only a brand new issuer appearing mid-outage is refused.
- [ ] No Resilience4j `@Retry` / `@TimeLimiter` / `@CircuitBreaker` on this path. The bounded timeouts plus
      the negative cache already give the fast-fail a breaker would, without putting an interceptor on every
      authenticated request, and a retry would multiply the time a request holds a thread while the provider
      is struggling. Revisit if metrics (Phase 6) show it is needed.

### K2. Quarkus keycloak — **[Q] S3 (config, not FT)**
Files: `quarkus/keycloak/.../KeycloakTenantConfigResolver.java`, `KeycloakTenantRepository.java`.
- [x] Recommended `quarkus.oidc.*` values documented in [resilience.md](resilience.md): `connection-timeout`,
      `connection-retry-count`, `connection-delay`, `jwks.cache-time-to-live` and `jwks.resolve-early`. No
      code and no annotations - the extension owns the fetching, and `resolve-early` additionally keeps the
      first request from paying for the key fetch.

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
