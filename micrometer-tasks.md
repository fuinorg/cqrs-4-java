# Micrometer Tasks — cqrs-4-java

Part of the cross-repo [Micrometer Instrumentation Roadmap](https://github.com/fuinorg/ddd-cqrs-4-java-example/blob/develop/micrometer-roadmap.md).
This is **downstream** — release after event-store-commons and ddd-4-java.

Micrometer is already used here, but only as `Gauge`/`MeterBinder` **state** meters
([OutboxMetrics](https://github.com/fuinorg/cqrs-4-java/blob/develop/quarkus/process-manager/src/main/java/org/fuin/cqrs4j/quarkus/pm/OutboxMetrics.java),
[ProjectionLagMetrics](https://github.com/fuinorg/cqrs-4-java/blob/develop/springboot/query-core/src/main/java/org/fuin/cqrs4j/springboot/query/core/view/ProjectionLagMetrics.java)).
These tasks add **Counter/Timer** throughput/latency/outcome meters on the hot paths and wire the
new esc/ddd decorators. Keep the established conventions: `cqrs4j.<area>.<metric>` name constants,
bounded-cardinality tags, `micrometer-core` `<optional>true</optional>` (Spring) / Quarkus BOM
(Quarkus), a `SimpleMeterRegistry` test per meter.

## Phase 1 — Command dispatch (⭐ 20% set)

- [ ] ⭐ `cqrs4j.command.dispatch` Timer + outcome Counter (tags `cmd.type`,
      `outcome=success|deduplicated|failed`) on both dispatchers — the "already handled ⇒ skip"
      branch already exists and maps to `outcome=deduplicated`:
  - [`QuarkusCommandDispatcher`](https://github.com/fuinorg/cqrs-4-java/blob/develop/quarkus/command/src/main/java/org/fuin/cqrs4j/quarkus/cmd/QuarkusCommandDispatcher.java)
    (inject `MeterRegistry`, create meters in constructor — Quarkus provides it).
  - [`CommandDispatcher`](https://github.com/fuinorg/cqrs-4-java/blob/develop/springboot/command-core/src/main/java/org/fuin/cqrs4j/springboot/command/core/CommandDispatcher.java)
    (Spring — meter creation behind a nested `@ConditionalOnClass(MeterRegistry.class)` config).

## Phase 2 — Projection & view catch-up

- [ ] [`SimpleJpaEventDispatcher`](https://github.com/fuinorg/cqrs-4-java/blob/develop/esc/src/main/java/org/fuin/cqrs4j/esc/SimpleJpaEventDispatcher.java)
      `dispatchEvents` / `dispatchEvent` → `cqrs4j.projection.apply` Timer + Counter (tag
      `event.type`). Complements the existing `cqrs4j.projection.lag` gauge (processing rate vs
      backlog). Keep metering optional at the neutral `esc` seam.
- [ ] View-manager catch-up loops → `cqrs4j.view.chunk` Timer + `cqrs4j.view.events` Counter:
  - [`SpringViewManager`](https://github.com/fuinorg/cqrs-4-java/blob/develop/springboot/query-core/src/main/java/org/fuin/cqrs4j/springboot/query/core/view/SpringViewManager.java)
  - [`QuarkusViewManager`](https://github.com/fuinorg/cqrs-4-java/blob/develop/quarkus/query/src/main/java/org/fuin/cqrs4j/quarkus/view/QuarkusViewManager.java)

## Phase 3 — Process-manager (add Counter/Timer next to the existing gauges)

- [ ] `CommandQueueExecutor.drain`/`deliver` → `cqrs4j.command.delivery` Timer (tag `outcome`) +
      `cqrs4j.command.batch.size` DistributionSummary:
  - [Quarkus](https://github.com/fuinorg/cqrs-4-java/blob/develop/quarkus/process-manager/src/main/java/org/fuin/cqrs4j/quarkus/pm/CommandQueueExecutor.java)
  - [Spring](https://github.com/fuinorg/cqrs-4-java/blob/develop/springboot/process-manager/src/main/java/org/fuin/cqrs4j/springboot/pm/core/CommandQueueExecutor.java)
- [ ] `CommandOutboxService.recordFailure` → `cqrs4j.command.failure` + `cqrs4j.command.deadletter`
      Counters (a *rate* to complement the existing dead-letter *gauge*):
  - [Quarkus](https://github.com/fuinorg/cqrs-4-java/blob/develop/quarkus/process-manager/src/main/java/org/fuin/cqrs4j/quarkus/pm/CommandOutboxService.java)
  - [Spring](https://github.com/fuinorg/cqrs-4-java/blob/develop/springboot/process-manager/src/main/java/org/fuin/cqrs4j/springboot/pm/core/CommandOutboxService.java)
- [ ] `ProcessTimeoutSweeper.drain`/`handle` → `cqrs4j.process.timeout.swept` Counter (tag
      `outcome`):
  - [Quarkus](https://github.com/fuinorg/cqrs-4-java/blob/develop/quarkus/process-manager/src/main/java/org/fuin/cqrs4j/quarkus/pm/ProcessTimeoutSweeper.java)
  - [Spring](https://github.com/fuinorg/cqrs-4-java/blob/develop/springboot/process-manager/src/main/java/org/fuin/cqrs4j/springboot/pm/core/ProcessTimeoutSweeper.java)

## Phase 4 — Wire the esc / ddd decorators (this is where the neutral-lib metrics come alive)

The `MeteredEventStore*` (esc) and `MeteredRepository` (ddd) decorators are instantiated here,
where a `MeterRegistry` exists.

- [ ] **Spring**: nested `@Configuration @ConditionalOnClass(MeterRegistry.class) @ConditionalOnMissingBean`
      producing the `MeteredEventStore*` / `MeteredRepository` beans — mirror the pattern in
      [`ProcessManagerConfig`](https://github.com/fuinorg/cqrs-4-java/blob/develop/springboot/process-manager/src/main/java/org/fuin/cqrs4j/springboot/pm/core/ProcessManagerConfig.java)
      (`OutboxMetricsConfig`).
- [ ] **Quarkus**: CDI producers with optional `MeterRegistry` injection that wrap the backend
      `EventStore` / repository beans in the metered decorators.

## Dependencies

- [ ] Spring modules touched: `micrometer-core` with `<optional>true</optional>`, no version
      (Spring Boot BOM manages it) — as already done in `springboot/query-core`.
- [ ] Quarkus modules touched: `micrometer-core` via the Quarkus BOM.
- [ ] Consume the new upstream artifacts (`esc-micrometer`, `ddd4j-micrometer`) via the respective
      BOMs.

## Tests

- [ ] One `SimpleMeterRegistry` test per new meter, mirroring the existing `OutboxMetricsTest` /
      `ProjectionLagMetricsTest`. Assert timer counts, tag values (`outcome=deduplicated` for the
      skip branch), and counter increments.
