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
        .callTimeout(Duration.ofSeconds(10))
        // ...
        .build();

new GrpcProjectionAdminEventStore(client, tenantContext, Duration.ofSeconds(10));
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

| Property | Default | Meaning |
|---|---|---|
| `org.fuin.cqrs4j.projection.breaker.delay` | `30000` | Milliseconds the breaker stays open before it probes the store again |
| `org.fuin.cqrs4j.projection.breaker.requestVolumeThreshold` | `4` | Attempts judged before the breaker may open |
| `org.fuin.cqrs4j.projection.breaker.failureRatio` | `0.5` | Share of failed attempts that opens the breaker |

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

Currently not configurable — the values are constants in `SpringViewManager`:

| Setting | Value |
|---|---|
| Attempts judged before the breaker may open | `4` |
| Failure rate that opens the breaker | `50 %` |
| Wait before the first probe | `5 s` |
| Growth factor per further failed probe | `2.0` |
| Upper bound for the wait | `5 min` |

No extra dependency is required; `resilience4j-circuitbreaker` comes with
`cqrs-4-java-springboot-query-core`.

### Why the two differ

Resilience4j supports an **exponentially growing** wait between probes, so a longer outage is probed ever
less often. SmallRye Fault Tolerance only supports a **fixed** delay, so the Quarkus side re-probes on a
constant interval. Both stop hammering an unreachable store; only the Spring side backs off progressively.

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

| Property (`org.fuin.cqrs4j.pm.cmdqueue.*`) | Default | Meaning |
|---|---|---|
| `connectTimeout` | `5s` | Time to wait for a connection |
| `requestTimeout` | `5s` | Time to wait for a response |
| `breaker.delay` *(Quarkus)* | `30s` | How long the breaker stays open |
| `breaker.requestVolumeThreshold` *(Quarkus)* | `4` | Deliveries judged before it may open |
| `breaker.failureRatio` *(Quarkus)* | `0.5` | Share of failures that opens it |
| `breaker.windowSize` *(Spring)* | `4` | Deliveries judged before it may open |
| `breaker.failureRatePercent` *(Spring)* | `50` | Share of failures that opens it |
| `breaker.initialWait` *(Spring)* | `5s` | Wait before the first probe |
| `breaker.maxWait` *(Spring)* | `5m` | Upper bound for the wait between probes |

On Quarkus the values are milliseconds; on Spring they are durations (`5s`, `1m`). As with the projection
catch-up, the Spring side backs off exponentially while the Quarkus side re-probes on a fixed interval.

**Quarkus applications must add `io.quarkus:quarkus-smallrye-fault-tolerance`** here too.

## Current limits

- There are **no per-call retries, bulkheads or rate limits**, and the breakers expose no metrics. For the
  outbox that is deliberate: the outbox itself is the retry mechanism, and retrying inside a delivery would
  multiply the time a wedged endpoint holds the drain thread.
- **Push mode** re-subscribes on a fixed interval rather than backing off progressively.
- Build timeouts (surefire/failsafe per-test and per-fork, and the GitHub job timeout) are a build concern
  rather than a runtime one; they live in the root `pom.xml` and `.github/workflows/maven.yml`.
