# Design Notes

Key, non-obvious design decisions in `cqrs-4-java`. The *why* behind how things are built; see `arch-review.md`
for the architectural overview.

## Backend-neutral library

The library never builds the `EventStore` or the serialization registry — the **application** does. Any
cross-cutting plumbing (event up-caster registry, projection lease, metrics) is therefore **collected from
application-provided beans**, not hard-coded: Spring uses `List<T>` injection + `@ConditionalOnMissingBean`,
Quarkus uses `@All List<T>` / CDI producers (mirroring how `List<View>` becomes the `ViewRegistry`).

## Read-side model (as built)

The view manager (`SpringViewManager` / `QuarkusViewManager`) **polls** `readAllEventsForward` from the stored
checkpoint (`ProjectionService` → `QryProjectionPosition`) and folds each chunk in a **`REQUIRES_NEW`
transaction that commits the view update and the checkpoint advance together**. This is a
database-checkpoint, poll-based catch-up: simple, atomic, easy to monitor (head − checkpoint = lag), and
**at-least-once** — a view may see a chunk again after a crash, so view handlers must be idempotent.

## Event versioning consumption

- Version up-casting itself lives in the event store; this library only **consumes** it. It provides
  `ConverterRegistration` (a descriptor: type, from-version, to-version, `Converter`) and a `ConverterRegistry`
  bean assembled from **all** application-provided `ConverterRegistration` beans (none ⇒ an empty pass-through
  registry).
- The application wraps the **deserializer side** of its `EventStore` with that registry, so projections and
  aggregate replay upcast automatically — with **no view-manager changes**, because they already read the
  already-deserialized `commonEvent.getData()`. Writes keep serializing at the latest version.
- Command-path versioning is **not** done yet: the `/cmd/{type}` path and the outbox carry a bare type name
  with no version.

## HA projections (distributed lease)

- Multi-instance safety uses a **TTL/expiry lease** (`ProjectionLeaseService`, per-runtime JPA impls over
  `SPRING_QRY_PROJECTION_LEASE` / `QUARKUS_QRY_PROJECTION_LEASE`), acquired with a pessimistic
  `SELECT … FOR UPDATE` and keyed by `streamId.asString()`. **Tenant isolation comes from the routing
  datasource** (`TenantRoutingDataSource`), exactly like the checkpoint — so there is **no tenant column**.
- The existing in-JVM lock (`ReentrantLock` / `Semaphore`) is kept for cheap same-instance overlap prevention;
  the lease adds cross-instance safety. It is **renewed per chunk** (inside the checkpoint transaction) so a
  long catch-up keeps the lease alive, and is **gated off by default**
  (`org.fuin.cqrs4j.projection.ha.enabled`; `.ttl`; `.owner`, blank ⇒ a per-instance random UUID).
- Because the checkpoint still commits per chunk, projections are **single-writer but at-least-once** — a lease
  lost mid-pass (e.g. a GC pause beyond the TTL) may reprocess the current chunk. Same idempotency assumption
  as above.

## Command idempotency (effectively-once receipt)

- The outbox delivers commands **at-least-once**, so an **optional** processed-command store makes receipt
  **effectively-once**: `ProcessedCommandStore` (interface in `core`; Spring JPA impl `QryProcessedCommandStore`
  over `SPRING_CMD_PROCESSED`) keyed by the command's `EventId`, which travels in the JSON body as `event-id`
  (no header/outbox change). Tenant isolation comes from the routing datasource — **no tenant column**.
- `CommandDispatcher` filters around the handler with **record-after-success** (skip if processed; else handle;
  `markProcessed` on success), and is **opt-in** — active only when a store bean is supplied.
- **Effectively-once, not exactly-once:** the handler's event-store append and the JPA dedup row are separate
  resources (no shared transaction), so a crash *between* them can re-run a command — covered by the aggregate's
  expected-version check. Each row records a `PROCESSED_TS` to support retention/cleanup.
- **Spring-only** (the command receiver lives in `command-core`). Demonstrated in the reference app
  (`CommandController` + sample command/handler) with a slice test.

## Observability (metrics)

- Metrics are Micrometer `MeterBinder`s (auto-bound in both Spring and Quarkus). Micrometer is an **optional**
  dependency and the beans are guarded (`@ConditionalOnClass`), so applications that don't use metrics are
  unaffected. Projection-lag gauge = head − checkpoint per view (via the neutral `ProjectionLag` helper);
  outbox-depth / dead-letter gauges are **Spring-only** (the process-manager outbox exists only there).

## Cross-cutting conventions (apply to every change)

- **Nullness:** JSpecify `@NullMarked` + NullAway/Error Prone on production code (tests excluded). A framework
  field injection (`@Inject` / `@ConfigProperty`) of a reference type may need an explicit initializer to
  satisfy NullAway.
- **ArchUnit (per module):** every top-level production class needs a thread-safety annotation
  (`@ThreadSafe` / `@Immutable` / `@NotThreadSafe`); each module keeps a package **allow-list** that must be
  extended for any newly imported package; a coverage rule requires a `*Test` per class (or `@TestOmitted`).
- **JDK 25:** the Spring-Boot-managed Byte Buddy rejects Java-25 class files, so any module that mocks with
  Mockito needs `-Dnet.bytebuddy.experimental=true` on the surefire JVM.
- **Dependency hygiene:** `maven-dependency-plugin` runs with `failOnWarning` — every used dependency must be
  declared explicitly.
