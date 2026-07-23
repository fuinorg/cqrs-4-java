# Resilience — what is implemented

How this library behaves when the event store or the database is unreachable, and what you can configure.

Two subsystems talk to something that can be down: the **projection catch-up** reads from the event store
and the database, and the **command outbox** delivers queued commands to the command endpoint over HTTP.
Both run on a schedule, and both are covered here.

# Projection catch-up

## The problem this solves

The projection catch-up runs on a schedule. When the event store is unreachable, every single tick used to
start another attempt, and each attempt blocked a thread until it eventually gave up. A store that hangs
rather than refuses connections was the worst case: nothing failed, the attempts simply piled up.

Three layers now bound that, from innermost to outermost.

## 1. Bounded event store calls (`esc`)

Every call of the gRPC event store client waits with a timeout instead of forever. If the client never
completes the call — the connection was never established, or the work item is queued behind a broken
connection — the call fails with `EventStoreCallTimeoutException` after **5 seconds** instead of blocking
the calling thread indefinitely.

A timeout is deliberately *not* reported as an `ExecutionException`, because the callers interpret that as
an answer from the server (`streamExists(..)` would turn "no answer" into "the stream does not exist").
A timeout means the outcome is unknown — for a write, it may or may not have been applied.

Configurable per event store instance:

```java
ESGrpcEventStore.builder()
        .

callTimeout(Duration.ofSeconds(10))
        // ...
        .

build();

new

GrpcProjectionAdminEventStore(client, tenantContext, Duration.ofSeconds(10));
```

## 2. Failure classification

`EscUtils.isTransientInfrastructureFailure(Throwable)` (module `cqrs-4-java-esc`) decides whether a failure
is an expected connectivity problem or an unexpected programming/configuration error.

The event store reports every "store or database not reachable" condition as an `EscConnectionException`,
so a single `instanceof` settles those. Anything else falls back to
`CqrsUtils.isTransientInfrastructureFailure(Throwable)` in `cqrs-4-java-core`, which stays free of any event
store dependency and matches gRPC transport errors, call timeouts, `java.net.*`, `java.io.IOException`,
`java.sql.*`, `jakarta.persistence.*` and `org.springframework.dao.*` by name - so failures raised *below*
the event store abstraction are still recognised.

**A failure that is an answer about the data is not transient**, even though it lives in those same packages.
`OptimisticLockException`, `DataIntegrityViolationException`, `DuplicateKeyException`,
`SQLIntegrityConstraintViolationException` and their siblings mean the database answered, and the answer will
be the same on the next attempt. They are excluded explicitly, so a constraint violation in one view stays
visible at `ERROR` instead of being logged as "will retry", and cannot open the shared projection breaker for
every other view. The whole cause chain is inspected, so a conflict wrapped in a `RollbackException` is
classified by the conflict rather than by the wrapper.

Use the `EscUtils` variant wherever an event store is involved, the `CqrsUtils` one where it is not.

It is used for two things:

- **Logging.** A transient failure is logged at `DEBUG` ("will retry on the next run"); anything else is
  logged at `ERROR`. Getting this wrong once hid a real bug behind a debug line for a long time, so the
  distinction is covered by tests.
- **The circuit breakers below**, so that a failing view handler or a configuration error can never open a
  breaker — only a store or database that cannot be reached.

## 3. Circuit breaker around the projection catch-up

Once the store is known to be unreachable, further attempts fail immediately instead of blocking a thread,
until the breaker probes the store again. **The schedule remains the ultimate self-heal**: the breaker only
decides whether a given tick even tries.

Both steps of a catch-up run are guarded and share one breaker, because it is the same store and the same
database behind them:

1. preparing the run — ensuring the projection exists and reading the checkpoint,
2. reading and dispatching the events themselves.

Only transient infrastructure failures count towards opening the breaker. A view handler that throws is a
processing error: it is logged at `ERROR` and never opens the breaker.

### Quarkus (SmallRye Fault Tolerance)

| Property                                                    | Default | Meaning                                                              |
|-------------------------------------------------------------|---------|----------------------------------------------------------------------|
| `org.fuin.cqrs4j.projection.breaker.delay`                  | `30000` | Milliseconds the breaker stays open before it probes the store again |
| `org.fuin.cqrs4j.projection.breaker.requestVolumeThreshold` | `4`     | Attempts judged before the breaker may open                          |
| `org.fuin.cqrs4j.projection.breaker.failureRatio`           | `0.5`   | Share of failed attempts that opens the breaker                      |

State changes are logged at `INFO`, so an open breaker is visible rather than silent.

**Your application must add the fault tolerance extension**, because the library only builds against the
APIs (same arrangement as `quarkus-scheduler-api`):

```xml

<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-fault-tolerance</artifactId>
</dependency>
```

Without it the projection catch-up fails at runtime when it creates its guard.

### Spring Boot (Resilience4j)

| Property                                                  | Default | Meaning                                     |
|-----------------------------------------------------------|---------|---------------------------------------------|
| `org.fuin.cqrs4j.projection.breaker-window-size`          | `4`     | Attempts judged before the breaker may open |
| `org.fuin.cqrs4j.projection.breaker-failure-rate-percent` | `50`    | Failure rate that opens the breaker         |
| `org.fuin.cqrs4j.projection.breaker-initial-wait`         | `5s`    | Wait before the first probe                 |
| `org.fuin.cqrs4j.projection.breaker-backoff-multiplier`   | `2.0`   | Growth factor per further failed probe      |
| `org.fuin.cqrs4j.projection.breaker-max-wait`             | `5m`    | Upper bound for the wait                    |

No extra dependency is required; `resilience4j-circuitbreaker` comes with
`cqrs-4-java-springboot-query-core`.

### Why the two differ

Resilience4j supports an **exponentially growing** wait between probes, so a longer outage is probed ever
less often. SmallRye Fault Tolerance only supports a **fixed** delay, so the Quarkus side re-probes on a
constant interval. Both stop hammering an unreachable store; only the Spring side backs off progressively.

## 4. Push-mode wake-up subscriptions

In `push` mode each view opens a subscription to new events, and an arriving event triggers the catch-up
pass immediately instead of waiting for the next tick. The event store does not re-subscribe by itself, so
`ViewSubscriptions` re-establishes a dropped subscription — and retries the very first one, which is what
lets an application start before the store is reachable.

The schedule is an event-store-commons `Backoff`, `Backoff.DEFAULT` in both frameworks:

| Setting                             | Value                                                    |
|-------------------------------------|----------------------------------------------------------|
| Delay before the first re-subscribe | `500 ms`                                                 |
| Growth factor per further failure   | `2.0`                                                    |
| Upper bound for the delay           | `30 s`                                                   |
| Jitter                              | `50 %` (the delay is spread over `[0.5 × delay, delay]`) |
| Attempt limit                       | none                                                     |

The cap keeps a long outage from turning into an even longer recovery, and the jitter keeps every instance
of a scaled-out service from reconnecting in lockstep and hitting the store as one burst the moment it
returns. The attempt counter is per stream and resets whenever a subscription is established, so a later
outage starts at the beginning of the schedule again.

Configurable per framework, with those values as the defaults:

| Spring Boot                                            | Quarkus                                                   |
|--------------------------------------------------------|-----------------------------------------------------------|
| `org.fuin.cqrs4j.projection.resubscribe-initial-delay` | `org.fuin.cqrs4j.projection.resubscribe.initial-delay-ms` |
| `org.fuin.cqrs4j.projection.resubscribe-max-delay`     | `org.fuin.cqrs4j.projection.resubscribe.max-delay-ms`     |
| `org.fuin.cqrs4j.projection.resubscribe-multiplier`    | `org.fuin.cqrs4j.projection.resubscribe.multiplier`       |
| `org.fuin.cqrs4j.projection.resubscribe-jitter-factor` | `org.fuin.cqrs4j.projection.resubscribe.jitter-factor`    |
| `org.fuin.cqrs4j.projection.resubscribe-max-attempts`  | `org.fuin.cqrs4j.projection.resubscribe.max-attempts`     |

**Losing a wake-up subscription costs latency, never correctness.** The scheduled poll keeps running and
reads from its checkpoint, which is why there is no attempt limit by default and why exhausting a configured
one is logged and accepted rather than escalated.

## 5. Deployment models: independent views vs. a shared read model

Which of the next two paragraphs applies to you is decided by a single setting, and it changes what the
library does at all. **Projections do not have to share a database.**

|                                         | Independent views (**default**)                               | Shared read model (HA)                                   |
|-----------------------------------------|---------------------------------------------------------------|----------------------------------------------------------|
| `org.fuin.cqrs4j.projection.ha.enabled` | `false`                                                       | `true`                                                   |
| Read model store                        | one per instance — in-memory, file, or its own schema         | one, shared by all instances                             |
| Projection position                     | per instance, so it resets on restart and the view is rebuilt | shared, so it survives a restart                         |
| Projection lease                        | **never used** — no lease table, no lock                      | one instance per projection holds the lease and projects |
| Instances                               | any number, fully independent of each other                   | any number, exactly one leader per projection            |

### Independent views (default)

With HA off, `readStreamEventsLeased(..)` returns before the lease service is ever called, so nothing in this
mode touches a lease table or takes a row lock. Every instance projects for itself.

This is what makes a view with its own in-memory database work: the position is stored next to the read
model, so an empty store on startup means position 0, and the view is simply rebuilt from the projection
stream. `esc-spi` also ships an `InMemoryCheckpointStore` if you want to keep that side out of JPA entirely.

The trade-off is read load rather than resilience: N instances each read the whole stream, so the event store
sees N times the traffic. That is inherent to the model.

### Shared read model (HA) and the projection lease

With `org.fuin.cqrs4j.projection.ha.enabled=true`, several instances write the *same* read model, so exactly
one of them may project at a time. Instances compete for a lease before projecting; the acquisition takes a
`PESSIMISTIC_WRITE` lock on the lease row and gives up after **3 seconds**
(`jakarta.persistence.lock.timeout`), configurable with
`org.fuin.cqrs4j.projection.lease-lock-timeout` (Spring) or
`org.fuin.cqrs4j.projection.lease-lock-timeout-ms` (Quarkus).

Giving up quickly is deliberate: not getting the lease means another instance is projecting, which is the
normal outcome rather than a failure. Without the bound, every instance that is not the leader would park a
thread on that row on every scheduled tick.

### Pool sizing

The library cannot size your HikariCP (Spring) or Agroal (Quarkus) pool, so size it yourself with the
projection work in mind. `tryLock` limits the catch-up to **one pass per view at a time**, so the worst case
a projection can occupy is:

```
concurrent projection connections  <=  number of views  (+1 per view while the lease transaction runs, HA only)
```

Keep the pool comfortably above that plus your request concurrency; otherwise a slow database lets projection
passes hold every connection and the request path starves. There is deliberately no bulkhead capping
projections further — it would delay them for a problem that correct pool sizing solves, and the scheduled
poll is already the safety net.

# Token validation (Keycloak)

Validating a token needs the identity provider's OpenID Connect configuration and its signing keys. The
first time an issuer is seen, that happens **on the request thread**, which makes a slow or down Keycloak a
problem for the whole service rather than only for authentication.

## Spring Boot

| Call                                                 | Bound                            | Notes                                                                                                                                           |
|------------------------------------------------------|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| OIDC discovery (`/.well-known/openid-configuration`) | **5 s** connect / 5 s read       | Override with the `sun.net.client.defaultConnectTimeout` / `defaultReadTimeout` system properties                                               |
| JWK set fetch                                        | **500 ms** connect / 500 ms read | Nimbus `RemoteJWKSet` defaults; override with `com.nimbusds.jose.jwk.source.RemoteJWKSet.defaultHttpConnectTimeout` / `.defaultHttpReadTimeout` |

**A failed discovery is remembered.** Without that, a Keycloak that is down is contacted again by every
single request carrying that issuer, each one holding a request thread until the call gives up. A failure is
cached per issuer and the delay doubles from **1 s** up to **30 s**, so a long outage costs one attempt per
window and every other request fails immediately.

**An issuer that was resolved once stays resolved.** The negative cache only covers discovery; a tenant
already in the cache keeps validating tokens from the keys Nimbus holds, so an outage never invalidates
issuers that were working. Only a *new* issuer appearing during an outage is refused.

All three behaviours - not retrying while down, retrying once the backoff elapsed, and continuing to serve an
issuer that was already resolved - are checked against a real Keycloak in `KeycloakOutageIT`.

## Quarkus

The application does not fetch anything itself — `quarkus-oidc` does. Tune it with configuration rather than
fault tolerance annotations:

| Property                               | Suggested | Meaning                                                   |
|----------------------------------------|-----------|-----------------------------------------------------------|
| `quarkus.oidc.connection-timeout`      | `5s`      | Bound for a single call to the provider                   |
| `quarkus.oidc.connection-retry-count`  | `3`       | Retries while the provider is starting up                 |
| `quarkus.oidc.connection-delay`        | `10s`     | How long startup waits for the provider to appear         |
| `quarkus.oidc.jwks.cache-time-to-live` | `10m`     | How long a fetched JWK set is reused                      |
| `quarkus.oidc.jwks.resolve-early`      | `true`    | Fetch the keys at startup instead of on the first request |

Setting `connection-delay` lets the service start before Keycloak is reachable, and `resolve-early` keeps the
first request from paying for the key fetch.

# Command receipt

## Inbound bulkhead on the command receiver

The receiving side deduplicates commands so a redelivery is not executed twice. That lookup hits the
database on every incoming command, which makes it the place where a slow database turns into exhausted
request threads. `BulkheadProcessedCommandStore` bounds how many lookups may run at once and refuses the rest
straight away, so the endpoint stays responsive for the traffic it can still handle.

**You have to wire it yourself.** The application supplies the command dispatcher and decides whether to give
it a `ProcessedCommandStore` at all, so the library cannot apply the bulkhead for you - it ships the decorator
and you wrap your store with it:

```java
// Spring
new BulkheadProcessedCommandStore(myProcessedCommandStore, 8,Duration.ofMillis(50));

// Quarkus
        new

BulkheadProcessedCommandStore(myProcessedCommandStore, 8);
```

Pick the limit from your pool and thread budget: it caps how many request threads can be inside a dedup
lookup at once.

There is deliberately **no retry** here — the sender's outbox is the retry mechanism.

A refused command is answered with **HTTP 503**. The sender classifies a 5xx as a transient delivery failure,
so the command is deferred and delivered again rather than counting towards its dead-letter budget.

**Only the lookup is guarded, never the record.** `processed(..)` runs before the handler does anything, so
refusing it costs nothing. `markProcessed(..)` runs after the handler succeeded: refusing it would leave the
command executed but unrecorded, and the next redelivery would execute it a second time. Load shedding must
never be able to create a duplicate side effect, so the record always goes through — even when the bulkhead
is full.

Both frameworks apply the bulkhead programmatically, for the same reason as the guards above: the annotation
would not fire on a bean the application wires up itself. Limits are constructor arguments today, not
configuration.

# Command outbox delivery

The process manager queues commands in an outbox and a scheduled drain delivers them over HTTP. The outbox
already gives durable redelivery and a dead-letter queue after `maxRetries` attempts (default 5).

## The problem this solved

Every failure looked the same. A connection error, a `503` from an overloaded endpoint and a `400` for a
malformed command all ended up as one generic exception, so the drain could not tell "try again later" from
"this will never work". Combined with a tick every 5 seconds and no timeouts, that meant:

- an unreachable endpoint blocked the drain thread until the operating system gave up, and
- **a 25 second outage permanently dead-lettered valid commands**, because every failed tick consumed one of
  the five attempts.

## What is in place

**Typed failures.** `TransientCommandDeliveryException` (endpoint unreachable, timed out, or answered 5xx)
and `CommandDeliveryException` (the endpoint answered and the command itself is the problem, typically 4xx).
The permanent case is checked first, so an answered 4xx stays permanent even if there is an `IOException`
somewhere in its cause chain.

**Timeouts.** Connect and request/read timeouts on both clients, 5 seconds each by default.

**Circuit breaker.** Trips only on transient failures, so a single rejected command never stops delivery for
all the others. While it is open **the batch is deferred untouched: nothing is recorded**, so an outage does
not consume the retry budget and cannot dead-letter commands that were never even sent. A rejected command
still records a failure and is still dead-lettered at `maxRetries`.

| Property (`org.fuin.cqrs4j.pm.cmdqueue.*`)   | Default | Meaning                                 |
|----------------------------------------------|---------|-----------------------------------------|
| `connectTimeout`                             | `5s`    | Time to wait for a connection           |
| `requestTimeout`                             | `5s`    | Time to wait for a response             |
| `breaker.delay` *(Quarkus)*                  | `30s`   | How long the breaker stays open         |
| `breaker.requestVolumeThreshold` *(Quarkus)* | `4`     | Deliveries judged before it may open    |
| `breaker.failureRatio` *(Quarkus)*           | `0.5`   | Share of failures that opens it         |
| `breaker.windowSize` *(Spring)*              | `4`     | Deliveries judged before it may open    |
| `breaker.failureRatePercent` *(Spring)*      | `50`    | Share of failures that opens it         |
| `breaker.initialWait` *(Spring)*             | `5s`    | Wait before the first probe             |
| `breaker.maxWait` *(Spring)*                 | `5m`    | Upper bound for the wait between probes |

On Quarkus the values are milliseconds; on Spring they are durations (`5s`, `1m`). As with the projection
catch-up, the Spring side backs off exponentially while the Quarkus side re-probes on a fixed interval.

**Quarkus applications must add `io.quarkus:quarkus-smallrye-fault-tolerance`** here too.

# What is deliberately absent

Several patterns you might expect are not here. Each is a decision rather than an omission.

**No per-delivery retry on the outbox.** The outbox *is* the retry mechanism: it stores the command durably
and the scheduled drain tries again. Retrying inside a single delivery would multiply the time a wedged
endpoint holds the drain thread, and the scheduler's `tryLock` already prevents overlapping runs.

**No `@Timeout` layer around the projection catch-up.** The operation bound comes from the event store call
timeout instead (5 s by default), one layer further in, where it can actually cancel the call.

**No `@TimeLimiter` on the Spring outbox.** It requires a `CompletableFuture` return type; the HTTP client's
own connect and read timeouts bound the call directly.

**No retry, time limiter or circuit breaker on token validation.** The bounded timeouts plus the negative
cache already give the fast-fail a breaker would, without putting an interceptor on every authenticated
request — and a retry would multiply the time a request holds a thread while the identity provider is
struggling.

**No `resilience4j.*` instance configuration.** The breakers are configured through the
`org.fuin.cqrs4j.*` keys instead, so there is one place to look regardless of framework. Adding
`resilience4j-spring-boot3` and `spring-boot-starter-aop` would only be needed for annotation-driven
configuration or actuator integration; the guards are applied programmatically, so no AOP proxies are
involved.

**No bulkhead capping projection work.** `tryLock` already limits the catch-up to one pass per view, so the
worst case is bounded by the number of views — a number you know when you size your pool. Capping it further
would delay projections for a problem that correct pool sizing solves.

**Some values are not configurable**, because they are already yours to choose or have no operational reason
to vary: the inbound bulkhead limits are the arguments you pass when you construct the decorator, and the
Keycloak negative-cache delays are internal to a bean the starter wires.

# Current limits

- The circuit breakers and bulkheads **expose no metrics**. The Quarkus side logs breaker state changes at
  `INFO`; the Spring side logs nothing.
- Build timeouts (surefire/failsafe per-test and per-fork, and the GitHub job timeout) are a build concern
  rather than a runtime one; they live in the root `pom.xml` and `.github/workflows/maven.yml`.
