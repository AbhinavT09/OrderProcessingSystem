---
title: Folder and Class Reference
nav_order: 8
---

# Folder and Class Reference

## Source Layout

- `src/main/java/com/example/orderprocessing/interfaces/http`
- `src/main/java/com/example/orderprocessing/application`
- `src/main/java/com/example/orderprocessing/domain/order`
- `src/main/java/com/example/orderprocessing/infrastructure` (includes `infrastructure/scheduling` for `PendingToProcessingScheduler`)
- `src/main/java/com/example/orderprocessing/config`

## Documentation Entry Points

- [Security and Authorization]({{ '/security-and-authorization/' | absolute_url }}) — roles, ownership, read/cancel rules
- [Reference Index]({{ '/reference/' | absolute_url }})
- [Interface HTTP Layer]({{ '/reference/api-layer/' | absolute_url }})
- [Application Layer]({{ '/reference/application-layer/' | absolute_url }})
- [Domain Layer]({{ '/reference/domain-layer/' | absolute_url }})
- [Infrastructure Layer]({{ '/reference/infrastructure-layer/' | absolute_url }})
- [Configuration and Runtime Controls]({{ '/reference/configuration-and-runtime/' | absolute_url }})

## Quick Responsibility Map

- **Interfaces**: endpoint contracts, request binding, error translation
- **Application**: use-case orchestration, transactions, port boundaries
- **Domain**: lifecycle invariants and state transitions
- **Infrastructure**: adapters (DB/Kafka/Redis/security/resilience)
- **Config**: runtime wiring and dependency-client setup

## Why This Package Structure Exists

The package layout is optimized for distributed-system correctness, not just code organization.

### `interfaces/http`: transport isolation

- Keeps HTTP concerns (validation, auth surface, error envelopes) out of domain code.
- Prevents accidental coupling of request DTOs to persistence entities.
- Makes API versioning and compatibility changes local to interface adapters.

### `application`: orchestration and write/read boundaries

- Centralizes idempotency lifecycle decisions and transactional boundaries.
- Contains ports so application services are testable without DB/Kafka/Redis.
- Separates write side (`OrderService`) and read side (`OrderQueryService`) for CQRS clarity.

### `domain/order`: invariants and optimistic concurrency semantics

- Encodes legal state transitions in state classes instead of controller conditionals.
- Keeps business rule changes independent of transport and storage details.
- Provides a single source of truth for conflict behavior.

### `infrastructure`: side effects and resilience mechanics

- Contains all adapters that talk to external systems.
- Houses lease fencing, retry orchestration, regional failover logic, and rate limiting.
- Ensures failure policy changes (fail-open vs fail-fast) are explicit and localized.

### `config`: runtime composition

- Wires concrete adapters to ports.
- Encapsulates client tuning (timeouts, retry knobs, circuit-breaker related configs).
- Keeps operational controls discoverable for production debugging.

## Directory-to-Guarantee Mapping

| Directory | Reliability Guarantee | Primary Terms |
|---|---|---|
| `application/service` | request-level idempotency and write gating | Idempotency, Write Gating |
| `domain/order/state` | legal lifecycle transitions | Optimistic Concurrency |
| `infrastructure/messaging` | reliable eventual publication | Lease Fencing, Eventual Consistency |
| `infrastructure/scheduling` | periodic `PENDING`→`PROCESSING` promotion | Fixed-rate job, transactional per order |
| `infrastructure/web/ratelimit` | admission control under load | Backpressure, fail-open |
| `infrastructure/resilience` | regional safety under dependency failures | Active/Passive mode, conflict suppression |
