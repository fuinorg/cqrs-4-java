# cqrs-4-java — Architecture Review (Event Sourcing focus)

**Scope:** CQRS, projections/views, checkpoints, and process managers in `cqrs-4-java` (`org.fuin.cqrs4j`, v0.7.0-SNAPSHOT).
**Method:** static review of `core`, `esc`, serialization modules, the Spring Boot module tree, the Quarkus module, and the integration tests / ArchUnit rules.
**Companion:** see [ddd-4-java/arch-review.md](https://github.com/fuinorg/ddd-4-java/tree/develop/arch-review.md) (the event-sourcing engine this builds on) and [event-store-commons/arch-review.md](https://github.com/fuinorg/event-store-commons/blob/develop/arch-review.md) (the `esc` event-store abstraction underneath both). 
Cross-cutting items — chiefly event versioning and subscriptions — are shared; the fix is placed at the lowest layer that owns it (often `esc`).

> **Baseline note (corrected after re-checking `esc`):** several things this review could flag are already provided by `org.fuin.esc:esc-api` 0.10.0 and should not be re-built here — a **versioned serialization substrate** (`EnhancedMimeType` with a `version` param; `DeserializerRegistry` keyed by `(type, version)`; `SerializedDataTypeRegistry`; a `Converter` interface), **subscriptions/catch-up** (`SubscribableEventStore`, `Subscription`), and **server-side projection admin** (`ProjectionAdminEventStore`). The cqrs-specific gaps below are what remains *on top of* that baseline.

---

## 1. What this project is

`cqrs-4-java` is the **CQRS layer** over `ddd-4-java` + **esc** (Event Store Commons). It does **not** event-source anything itself; it provides:

- **Write side:** `Command` (a `ddd4j` `Event`), `CommandHandler`/`CommandHandlerRegistry` (Jandex-discovered), `CommandExecutor`/`MultiCommandExecutor`, `CommandExecutionContext` (tenant+user), `CommandAuthorizer`.
- **Read side:** `View` (a projection: name, event-type set, cron, chunk size, `handleEvents`), `ViewRegistry`, and per-framework **view managers** that fold events into read models with a **durable checkpoint**.
- **Process managers:** `ProcessManagerView` + a **transactional outbox** (`CommandOutbox`/`CommandOutboxService`) drained over HTTP by `CommandQueueExecutor` with retry → dead-letter.
- **Framework adapters:** a rich **Spring Boot** tree (common, command-core/-starter, query-core/-starter, keycloak, process-manager) and a single **Quarkus** module.

---

## 2. Read-side / projection design as built

The projection engine is the architectural heart and worth stating precisely:

1. At startup each `View` is registered against the framework scheduler with its **cron** expression. The projection id = `viewName + "-" + adler32(eventTypes)` (`CqrsUtils`), so **changing a view's event-type set yields a new projection name → automatic rebuild** (nice "cattle-not-pets" property).
2. A server-side **esc projection** is created via `ProjectionAdminEventStore.createProjection(...)` that funnels the chosen event types into one projection stream.
3. On each cron tick, the manager (`SpringViewManager` / `QuarkusViewManager`) **polls** `eventstore.readAllEventsForward(projectionStreamId, nextPos, chunkSize, …)`, and per chunk opens a **`REQUIRES_NEW`** transaction that calls `view.handleEvents(events)` and updates the checkpoint **in the same transaction** (`ProjectionService` → `QryProjectionPosition` row, `…_QRY_PROJECTION_POS`).
4. Overlap is prevented by an **in-process** lock (Spring: `ReentrantLock`; Quarkus: `Semaphore`). Replay = `resetProjectionPosition(streamId)`.

This is a **Database-Checkpoint, poll-based catch-up** design: simple, atomic (data+checkpoint commit together → idempotent, replay-safe), and easy to monitor (checkpoint vs. stream head = lag). Process-manager delivery is a textbook **transactional outbox**: `enqueue` runs in the view's transaction; an independent scheduled drainer delivers each command via `POST /cmd/{type}`, deleting on success and incrementing retries → dead-letter on failure, each mutation in its own `REQUIRES_NEW` transaction.

---

## 3. Strengths

1. **Crisp CQRS separation** with a framework-agnostic `core`, and **two real adapter stacks** (Spring Boot + Quarkus) sharing one `View`/`ProjectionService` contract.
2. **Checkpoint design is right:** read-model write and checkpoint advance commit **atomically**; the adler32-of-event-types projection naming forces clean rebuilds on schema change.
3. **Transactional outbox is solid** — atomic enqueue, per-command isolated drains, retry + dead-letter, optional `CommandAuthProvider` for auth headers. Recently made Spring-free at the interface (`CommandOutbox`, `ProcessManagerView` in `core`, JDK `HttpHeaders`).
4. **Multi-tenancy** threads through projections (`TenantIdsSupplier` + writable tenant context).
5. **Operational replay** is a first-class operation (`resetProjectionPosition`).
6. **Strong governance:** Jandex command-handler discovery, ArchUnit dependency/thread-safety rules per module, Testcontainers integration tests covering command→event→view and the full outbox flow.

---

## 4. Gaps & risks (event-sourcing lens)

> Ordered roughly by operational impact.

### 4.1 No horizontal scale-out / HA story for projections
Overlap protection is an **in-process** `ReentrantLock`/`Semaphore`. Run **two app instances** and both will poll and fold the same projection stream — double-processing read-model writes. There is no distributed lock, lease, or leader election, and no row-level checkpointing to make concurrent engines conflict-safe. Today projections are effectively **single-active-instance**, which caps availability and throughput.

### 4.2 Latency is bounded by the cron interval (poll, not push)
The view managers poll `readAllEventsForward` on a schedule **even though the very same `esc` library already provides `SubscribableEventStore`/`Subscription`** (catch-up + live). So this isn't a missing capability — it's an *unused* one: low-latency read models need tight crons (more empty polls / DB load) and still lag up to one interval.

### 4.3 Event versioning: substrate is in esc, but not exploited here
*Corrected after re-checking `esc`.* esc supports version-keyed deserialization (§baseline note), so per-version reads are **possible** — but neither `ddd-4-java` nor this project threads a version through, and there's **no upcaster chain** (esc's `Converter` is unused). Net effect on the cqrs paths:
- Projections replay history forever, so views must read **old** event versions; without an applied upcaster this pushes per-version `if`-logic into `handleEvents`.
- Commands are serialized to the outbox with Jackson and delivered later. A **mid-flight deploy** that changes a command's schema between enqueue and drain (or between producer and the `/cmd/{type}` receiver) can fail deserialization — the `ddd4j` event/command base classes are **not** `@JsonIgnoreProperties(ignoreUnknown=true)` (see `ddd-4-java/arch-review.md` §4.1). The proper fix lives in esc/ddd-4-java; this project should consume it.

### 4.4 Outbox delivery is at-least-once, but receiver idempotency is implicit
Re-delivery of the same command (`EventId` is the outbox PK and travels with it) is possible on retry, yet there is no provided **dedup/idempotency store** on the command receiver. Correctness currently relies on each command handler being idempotent (or the aggregate's expected-version check rejecting the dupe) — workable, but undocumented and easy to get wrong.

### 4.5 Process-manager versioning / lifecycle is unaddressed
`ProcessManagerView` is a thin enqueue-in-transaction helper. There is no notion of **process version**, no timeout/zombie handling for processes waiting on a reply that never comes, and no takeover/hand-off mechanism. For short outbox flows this is fine; for any longer-running coordination it is a latent gap.

### 4.6 Quarkus is not at parity
Quarkus has the query/view engine but **no process-manager, command-core, or keycloak** equivalents. Mixed-runtime or Quarkus-first users get a smaller framework.

### 4.7 Eventual-consistency is not surfaced to read clients
Checkpoints are per-projection (bucket-level). The current checkpoint position is not returned with query results, so clients can't do optimistic-concurrency / "is my read fresh enough?" decisions, and there's no per-row checkpoint to enable that.

---

## 5. Recommendations — where to evolve, and how

### P1 — Make projections safe to run multi-instance (HA + scale)
Pick one, lowest-friction first:
- **(a) Distributed lease per view:** replace the in-process lock with a DB-backed lease/advisory lock (e.g. a `PROJECTION_LEASE` row with owner+expiry, or `SELECT … FOR UPDATE`/Postgres advisory lock) so exactly one instance runs a given view at a time → safe active/standby HA.
- **(b) For scale-out:** partition a view into independently-checkpointed **sub-projections** (by key hash), each leased separately. Combine with §P6 row-level checkpoints to let multiple engines write the same model conflict-free.
- **How:** the `ProjectionService` interface is the natural seam — add `tryAcquire(streamId, owner, ttl)` / `renew` / `release` and have both view managers call it instead of the local lock.

### P2 — Use esc's subscriptions for a push ViewManager (capability already exists)
This is wiring, not new infrastructure: add a continuous mode on top of `esc`'s `SubscribableEventStore`/`Subscription` — subscribe from the stored checkpoint, fold events as they arrive, persist the checkpoint atomically, fall back to catch-up polling on reconnect. Keep cron-poll as the default/batch mode; let a `View` opt into `LIVE` vs `SCHEDULED`. Removes interval latency and empty-poll load for hot read models.

### P3 — Consume the event/command versioning fix (owned by esc/ddd-4-java)
The mechanism belongs one/two layers down (see `event-store-commons/arch-review.md` §P1 and `ddd-4-java/arch-review.md` §P1). This project's part is to **consume** it: ensure the `/cmd/{type}` receiver and the outbox producer are **weak-schema across a rolling deploy** (additive-tolerant + version-keyed deserialize / upcast), so `handleEvents` and command delivery always see the current version. Document that contract for command handlers.

### P4 — Provide first-class command idempotency
Ship an optional **processed-command store** (table keyed by command `EventId`, scoped by tenant) and a thin receiver-side filter so at-least-once outbox delivery becomes **effectively-once** without relying on every handler being hand-written idempotent. Document the contract either way.

### P5 — Process-manager lifecycle hardening
For anything beyond fire-and-forget:
- Add **timeouts** to process steps (no infinite waits → no zombies) and a scheduled sweep that flags/repairs stuck instances.
- Introduce a **process-version** tag and the **takeover/hand-off** pattern (end old instance → emit a takeover event on the same correlation id → new version resumes), and recommend **new-process-not-in-place-upgrade** as the default. Consider an event-sourced `ProcessManagerView` variant so a new version can replay history and derive state the old one never stored.

### P6 — Surface eventual consistency to clients
Optionally store the checkpoint **alongside read-model rows** (or return the projection position with query results) so clients get "this data is current as of position N" → enables optimistic concurrency and freshness checks, and is the prerequisite for the conflict-free multi-engine writes in §P1(b).

### P7 — Close Quarkus parity
Port process-manager (outbox), command-core, and keycloak support to Quarkus so both runtimes offer the same surface; the framework-agnostic `core` already holds the shared abstractions, so most of the work is the CDI/JTA adapter layer.

### P8 — Expose projection lag metrics
The checkpoint model makes this nearly free: publish `head_position − checkpoint` per view (and outbox depth / dead-letter count) as Micrometer/MP-Metrics gauges for SLA monitoring and replay progress.

---

## 6. Roadmap snapshot

| Priority | Item | Effort | Risk if skipped |
|---|---|---|---|
| **P1** | Distributed lease → multi-instance-safe projections | M | High — silent double-processing under HA |
| **P2** | Push ViewManager using esc subscriptions (low-latency mode) | M | Medium — interval latency + poll load |
| **P3** | Consume esc/ddd4j versioning fix in cmd + view paths | S–M | High — rolling deploys break (de)serialization |
| **P4** | Effectively-once command receipt (dedup store) | S–M | Medium — duplicate side-effects on retry |
| **P5** | Process-manager timeouts/versioning/takeover | M | Medium — zombies, unversionable long processes |
| **P6** | Checkpoint-with-rows / freshness to clients | S–M | Low–Med — no optimistic-concurrency reads |
| **P7** | Quarkus parity (PM, command-core, keycloak) | M–L | Medium — uneven runtime support |
| **P8** | Projection-lag / outbox metrics | S | Low — weaker operability |

**Already strong, keep as-is:** the CQRS core split, atomic Database-Checkpoint projections, adler32 rebuild-on-schema-change, the transactional outbox (enqueue-in-tx + isolated retried drains + dead-letter), multi-tenancy, replay-as-an-operation, and the ArchUnit/Jandex/Testcontainers discipline.

---
*Generated as an architectural review; no code was modified. File paths reference `org.fuin.cqrs4j` modules under this repository. The `esc-api` capabilities cited (`SubscribableEventStore`, `ProjectionAdminEventStore`) come from `org.fuin.esc:esc-api` 0.10.0.*
