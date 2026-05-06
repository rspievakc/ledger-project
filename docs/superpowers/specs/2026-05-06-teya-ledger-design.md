# Spec — Teya Ledger Service (2026-05-06)

This spec is the canonical brainstorming output, scoped tightly so it
can be handed to the writing-plans skill to produce an implementation
plan. Architectural depth lives in
[`docs/architecture.md`](../../architecture.md); the build-ordered
how-to lives in [`docs/implementation.md`](../../implementation.md);
this doc focuses on the contract: **what** is being built and **what
"done" looks like**.

---

## 1. Goal

Build a Spring Boot 3 / Java 25 HTTP service implementing an
event-sourced money ledger. Customers can hold multiple accounts;
each account has a fixed currency and a per-account configurable
overdraft limit. The service supports deposits, withdrawals, balance
queries, and paginated transaction history, persists state to YAML
event-stream files on disk, and ships as an OCI container image with
OpenAPI documentation.

### Out of scope (deliberately)

- Authentication / authorisation (documented as a future improvement).
- Inter-account transfers.
- Multi-currency accounts (each account has exactly one currency).
- Foreign-exchange operations.
- Persistent idempotency store (in-memory only).
- Mutation testing, load/perf tests, contract tests.

---

## 2. Functional requirements

| ID | Requirement |
| --- | --- |
| F1 | Create a customer (`POST /customer`). |
| F2 | Open an account for a customer with a given currency and overdraft limit (`POST /customer/{id}/account`). New accounts always start with a zero balance. |
| F3 | Deposit money into an account (`POST /account/{id}/deposit`). |
| F4 | Withdraw money from an account (`POST /account/{id}/withdrawal`). |
| F5 | Change the overdraft limit on an existing account (`PATCH /account/{id}/overdraft-limit`). |
| F6 | Read current account state including balance (`GET /account/{id}`). |
| F7 | List the transaction history of an account, cursor-paginated (`GET /account/{id}/transaction?after=<seq>&limit=<n>`). |
| F8 | Close an account, only if the balance is exactly zero (`DELETE /account/{id}`). |
| F9 | Every write endpoint requires an `Idempotency-Key` HTTP header; replays return the original response; same key with a different body returns `409`. |
| F10 | OpenAPI documentation served at `/swagger-ui.html` and `/v3/api-docs`. |
| F11 | Project ships with a `README.md` that documents quick-start, API surface, architecture, and how to add a new storage adapter. |
| F12 | Project ships as an OCI container image built via `./gradlew bootBuildImage`, runnable through a `docker-compose.yml` that mounts `./data` as a persistent volume. |

---

## 3. Non-functional requirements

| ID | Requirement |
| --- | --- |
| N1 | Comprehensive automated test suite: unit tests for domain + application layers, adapter tests for the `YamlEventStore`, integration tests for every HTTP endpoint, plus an end-to-end scenario test. |
| N2 | Jacoco line coverage ≥ 95% on `com.teya.ledger.domain` and `com.teya.ledger.application` packages, enforced by a Gradle verification task. |
| N3 | Money values are represented internally as `long` minor units paired with an ISO currency code; cross-currency arithmetic is impossible at the value-object level. |
| N4 | Concurrent writes against the same account are linearisable via a per-account `ReentrantLock`; concurrent writes against different accounts run in parallel. |
| N5 | YAML append writes are durable: every append is `fsync`'d before the call returns. A torn final document at the file tail (write killed mid-fsync) is detected on read and ignored — no event reported as persisted to a caller is ever lost. |
| N6 | The HTTP layer never mentions storage or YAML; the domain layer never mentions Spring or HTTP; storage adapters depend on a port interface. Swapping `YamlEventStore` for another `EventStore` adapter requires changes only in `infrastructure.config`. |
| N7 | Errors return a stable JSON envelope (`{code, message, details, requestId}`) with one of a fixed set of codes documented in [`architecture.md` §8](../../architecture.md#8-error-model). |
| N8 | Each request gets a UUID correlation id placed in MDC under `requestId` and echoed in 4xx/5xx response bodies. |
| N9 | All design decisions are documented under `docs/architecture.md` and `docs/implementation.md`, including alternatives considered with pros/cons. Diagrams are written in Mermaid. |

---

## 4. Architecture summary

Hexagonal layout. Three layers under `com.teya.ledger`:

- `api` — controllers, DTOs, error mapper.
- `application` — command handlers, `LockRegistry`, `ProjectionCache`.
- `domain` — `Money`, `Customer`, `Account`, sealed event interfaces,
  typed exceptions. Pure Java, no Spring.
- `infrastructure` — `EventStore` and `IdempotencyStore` ports plus
  their adapters (`YamlEventStore`, `InMemoryEventStore`,
  `InMemoryIdempotencyStore`).

Persistence: one YAML file per stream under
`./data/streams/{customers.yaml | account-<uuid>.yaml | _health.yaml}`.
Each event is wrapped in an envelope with a monotonic per-stream
`seq` (which doubles as the pagination cursor).

Concurrency: per-account `ReentrantLock` held across the
`load → validate → append → cache update` sequence inside command
handlers.

Idempotency: `IdempotencyStore` (in-memory, LRU + TTL bounded) caches
`key → (eventId, requestHash, response)`. Lookups happen at the
controller boundary before the lock is acquired.

Container: `bootBuildImage` (Paketo Buildpacks). `docker-compose.yml`
mounts `./data` as a volume. No `Dockerfile`.

Full alternatives + pros/cons live in
[`docs/architecture.md`](../../architecture.md).

---

## 5. Public API contract

URL nouns are **singular** (deliberate departure from REST plural
convention). All write endpoints require `Idempotency-Key` header.

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/customer` | Body `{name}` |
| `GET` | `/customer/{customerId}` | |
| `POST` | `/customer/{customerId}/account` | Body `{currency, overdraftLimitMinorUnits}`; opens at zero balance |
| `GET` | `/account/{accountId}` | Returns id, customer, currency, overdraft, status, balance, last seq |
| `POST` | `/account/{accountId}/deposit` | Body `{amountMinorUnits, currency}` |
| `POST` | `/account/{accountId}/withdrawal` | Body `{amountMinorUnits, currency}` |
| `PATCH` | `/account/{accountId}/overdraft-limit` | Body `{newLimitMinorUnits}` |
| `DELETE` | `/account/{accountId}` | Refuses unless balance is exactly zero |
| `GET` | `/account/{accountId}/transaction` | Query `after` (default 0), `limit` (default 50, max 200) |

Error codes (full table in
[`architecture.md` §8](../../architecture.md#8-error-model)):

`IDEMPOTENCY_KEY_REQUIRED`, `INVALID_REQUEST`, `INVALID_AMOUNT`,
`CURRENCY_MISMATCH`, `INSUFFICIENT_FUNDS`, `ACCOUNT_CLOSED`,
`ACCOUNT_NOT_EMPTY`, `CUSTOMER_NOT_FOUND`, `ACCOUNT_NOT_FOUND`,
`IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST`, `INTERNAL_ERROR`.

---

## 6. Acceptance criteria

The project is "done" when:

1. `./gradlew check` passes locally and on a clean clone (tests + 95%
   coverage gate on domain + application).
2. `./gradlew bootRun` starts the service on `:8080` and
   `/actuator/health` returns `UP`.
3. `./gradlew bootBuildImage && docker compose up` starts the service
   in a container with persistent state in `./data`.
4. Every endpoint in §5 is implemented, documented in OpenAPI, and
   exercised by an integration test.
5. Every error code in §5 is reachable by at least one test that
   asserts the resulting HTTP status and `code` field.
6. The end-to-end scenario test passes: open customer → open two GBP
   accounts → deposits on each → cross-currency withdrawal rejected →
   paginated history returns the expected events in order → close on
   non-zero balance rejected → withdraw to zero → close succeeds.
7. Restarting the JVM (or the container) preserves all **ledger
   state** — a fresh process loaded against an existing `./data`
   directory returns identical balances and history. (Idempotency
   cache is in-memory only and is *expected* to clear on restart;
   this is documented and out of scope, see §1.)
8. The same idempotency key replayed against a write endpoint with
   the same body returns the original response; replayed with a
   different body returns `409`.
9. `docs/architecture.md`, `docs/implementation.md`, and `README.md`
   exist and are consistent with the implementation.
10. No layer-crossing imports: domain imports nothing from `api`,
    `application`, or `infrastructure`; `application` imports only
    domain + ports, never adapters; controllers import only DTOs +
    application services + error types. (Verified by code review at
    this scope; ArchUnit is a future improvement.)

---

## 7. Hand-off

This spec hands off to `superpowers:writing-plans` next. The plan
should:

- Use the milestones in
  [`implementation.md` §7](../../implementation.md#7-implementation-order-milestones)
  as the top-level structure.
- Include explicit verification commands at each milestone (the
  relevant `./gradlew test --tests …` invocation), per the
  `verification-before-completion` discipline.
- Mark the M2 → M3 transition (adapter tests must be green before
  any service code is written) and the M5 → M6 transition (every
  endpoint must have at least one passing IT before OpenAPI
  annotations are added) as review checkpoints.
