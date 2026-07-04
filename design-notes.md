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

## Read-model freshness

- Clients can ask **how fresh a view is**: `ProjectionFreshnessService` (Spring) /
  `QuarkusProjectionFreshnessService` (Quarkus) expose `position(view)` (the current checkpoint — a **cheap** read,
  used as a per-request `X-Projection-Position` header) and `freshness(view)` → `{position, lag, caughtUp}` (lag
  reads the event store forward via the neutral esc `ProjectionFreshness`/`ProjectionLag`, so it backs a dedicated
  `GET /freshness/{view}` endpoint, not every read). Stream-id mapping is centralized in `ProjectionStreamIds.of`.
- Reads run in the **ambient tenant context**; clients get read-your-writes by **polling** until `caughtUp`.
- **Number-space note:** the position is the *projection-stream* checkpoint, not the *aggregate version* a write
  returns — so a write→wait *token* isn't offered (would need the write path to return its position). Both runtimes.

## Event versioning consumption

- Version up-casting itself lives in the event store; this library only **consumes** it. It provides
  `ConverterRegistration` (a descriptor: type, from-version, to-version, `Converter`) and a `ConverterRegistry`
  bean assembled from **all** application-provided `ConverterRegistration` beans (none ⇒ an empty pass-through
  registry).
- The application wraps the **deserializer side** of its `EventStore` with that registry, so projections and
  aggregate replay upcast automatically — with **no view-manager changes**, because they already read the
  already-deserialized `commonEvent.getData()`. Writes keep serializing at the latest version.
- **The command path is symmetric with events and format-agnostic.** The command is serialized by the esc
  `Serializer` whose `EnhancedMimeType` (base type + encoding + version) is carried end to end as the HTTP
  `Content-Type` (e.g. `application/xml;version=2`, not just JSON). The receiver deserializes by that media type
  through the **same** `UpcastingDeserializerRegistry` + `ConverterRegistry` and up-casts to its local latest
  class (the dispatchers hold a `DeserializerRegistry` instead of binding raw JSON; base type and version come
  from the request, encoding falls back to the registry default). The process-manager outbox persists the full
  `CMD_CONTENT_TYPE` (from the serializer's mime) and stamps it on delivery. Both runtimes; the sender writes at
  the latest version, so a rolling deploy accepts an older command (up-cast) or a newer one (additive-safe).

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
- **Both runtimes.** Spring `CommandDispatcher` (`command-core`) and Quarkus `QuarkusCommandDispatcher`
  (`quarkus/.../cmd`) run the same neutral pipeline (type-resolve → validate → authorize → dedup → dispatch),
  differing only in glue: `ApplicationContext.getBean` ↔ CDI `Instance<CommandHandler>.select`, Jackson
  `ObjectMapper` ↔ JSON-B `Jsonb`, `QryProcessedCommandStore` (`SPRING_CMD_PROCESSED`) ↔
  `QuarkusProcessedCommandStore` (`QUARKUS_CMD_PROCESSED`). The receiver controller/resource lives in the app
  (Spring `CommandController` / Quarkus `CommandResource`), each demonstrated with a sample command/handler.

## Process-manager timeouts (no zombies)

- A **decoupled process-timeout registry** stops a PM that awaits a never-arriving reply from hanging:
  `ProcessTimeoutService` (interface in `core`; Spring JPA impl `ProcessTimeoutRepository` over
  `SPRING_PM_TIMEOUT`) with `arm(...)` / `cancel(processId)`, called **inside the view transaction** (like
  `CommandOutbox.enqueue`) so they commit atomically with the state change. One pending timeout per `processId`.
- `ProcessTimeoutSweeper` (mirrors `CommandQueueExecutor`: `@Scheduled`, `ReentrantLock`, per-item `REQUIRES_NEW`)
  hands each due row to the app's `ProcessTimeoutHandler` SPI **and** deletes it in one transaction; repeated
  failure → `SPRING_PM_TIMEOUT_DEAD_LETTER`. Inactive unless exactly one handler bean exists; handlers must be
  idempotent. Gauges `cqrs4j.process.timeout.pending` / `.overdue` surface stuck processes.
- **Process-version / takeover:** the version rides on each timeout → the handler, so a new version can ignore or
  adopt stale timeouts. Takeover/hand-off is a documented pattern (end old instance, emit a takeover event on the
  correlation id, resume in a new version) — not machinery.
- **Both runtimes.** The whole process-manager (transactional **outbox** delivery + this timeout registry) runs on
  Spring **and** Quarkus, sharing the neutral `core` SPIs (`CommandOutbox`, `ProcessTimeoutService`/`Handler`,
  `CommandAuthProvider`). Quarkus glue: `@ApplicationScoped` services + `@Transactional(REQUIRES_NEW)` (the
  sweeper opens the handle+delete tx via `QuarkusTransaction`), `io.quarkus.scheduler.@Scheduled` cron, a
  `java.net.http.HttpClient` command client (pairs with the `HttpHeaders`-based `CommandAuthProvider`), configured
  JSON-B via `JsonbProvider`, and `QUARKUS_PM_*` tables. Behaviour is covered by library unit tests (Spring adds
  Docker-free slice tests; the live outbox→`/cmd` round-trip is IT territory).

## Keycloak / OIDC authentication

- Both runtimes derive the `CommandExecutionContext` (tenant + user) from the request's OIDC bearer token and
  populate the writable tenant context from the Keycloak **realm** (the segment after the last `/` of the `iss`
  claim → `TenantId`). Spring: `KeycloakTokenWrapper` over a `JwtAuthenticationToken`, whose `NimbusJwtDecoder`
  pushes the realm into the `WritableTenantContext` on each decode (`keycloak-core`/`keycloak-starter`). Quarkus:
  `JsonWebTokenCommandExecutionContext` over MicroProfile `JsonWebToken`, with a JAX-RS filter
  (`TenantContextRequestFilter`) that sets the realm on entry and clears it on response (`quarkus/keycloak`).
- **Multi-tenant.** Realms are discovered lazily by issuer and cached (`KeycloakTenantRepository`); a
  `TenantAddedEvent` is fired when a new tenant appears so the query side can provision its projection/datasource.
  Spring selects per-realm signing keys via a `JWTClaimsSetAwareJWSKeySelector`; Quarkus delegates OIDC discovery,
  JWKS and issuer validation to `quarkus-oidc`, with a `TenantConfigResolver` mapping the token issuer to a
  per-realm tenant config.
- Neutral types (`TenantId`, `User`, `SimpleRole`, `WritableTenantContext`/`ThreadLocalTenantContext`,
  `TenantAddedEvent`) are shared. The reference apps ship a fixed system context, so the keycloak modules are
  validated by their own unit tests (a live Keycloak round-trip is IT territory).

## Observability (metrics)

- Metrics are Micrometer `MeterBinder`s (auto-bound in both Spring and Quarkus). Micrometer is an **optional**
  dependency and the beans are guarded (`@ConditionalOnClass`), so applications that don't use metrics are
  unaffected. Projection-lag gauge = head − checkpoint per view (via the neutral `ProjectionLag` helper);
  outbox-depth / dead-letter and process-timeout pending/overdue gauges are on **both runtimes** (Quarkus binds
  the `MeterBinder` beans via quarkus-micrometer).

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
