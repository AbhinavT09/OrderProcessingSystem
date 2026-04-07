**Live documentation:** **[https://abhinavt09.github.io/OrderProcessingSystem/](https://abhinavt09.github.io/OrderProcessingSystem/)**

---

# Order Processing System

Production-ready Spring Boot service for reliable e-commerce order processing with strong write correctness, asynchronous event delivery, and operational resilience.

## Project Highlights

- Hexagonal architecture with clear **Interface / Application / Domain / Infrastructure** boundaries
- CQRS split:
  - write orchestration in `OrderService`
  - read orchestration in `OrderQueryService`
- Domain state pattern for lifecycle transitions (`OrderState` hierarchy)
- Transactional outbox pattern for durable, async Kafka publication
- Global idempotency lifecycle (`IN_PROGRESS`, `COMPLETED`) for safe create retries
- Regional active/passive write gating with dependency health checks
- Redis-backed cache and distributed rate limiting with degradation safeguards
- JWT auth + RBAC + standardized API error contracts
- **Resource-level authorization:** order ownership stored as JWT `sub`; non-admins are scoped on **read, list, and cancel**; admins bypass scope
- Micrometer metrics, request correlation, and tracing integration

## Documentation

| Resource | Description |
|----------|-------------|
| **GitHub Pages** | Full architecture, operations, reference layers, and failure scenarios — link at top of this file |
| `docs/` (Jekyll) | Same content as the site; build locally with `bundle exec jekyll serve` from `docs/` if needed |
| Layer reference | [`docs/reference/`](docs/reference/) — API, application, domain, infrastructure, configuration |

Authoritative deep dives (on the live site and in-repo):

- [Design and Architecture](docs/design-and-architecture.md) — outbox, scheduling vs Kafka, consistency
- [Security and Authorization](docs/security-and-authorization.md) — roles, ownership, cache key isolation, `404` vs `403`
- [Observability and Operations](docs/observability-and-operations.md) — metrics, logging, alerts
- [Testing and Quality](docs/testing-and-quality.md) — suites, CI, residual gaps

## Core Features

### Order lifecycle and APIs

- Create orders with optional `X-Idempotency-Key` (ownership is bound to the JWT **`sub`** of the caller, exposed as `Authentication.getName()`)
- **Read scope:** `GET /orders/{id}`, `GET /orders`, `GET /orders/page` — non-**ADMIN** callers only see **their** orders (`owner_subject` match); **ADMIN** sees all. Cross-tenant access by id returns **`404`** (not `403`) to avoid leaking existence.
- Update status with optimistic concurrency version (**admin-only** route)
- Cancel while `PENDING`: **USER**/**ADMIN** may call cancel; non-admins only if they own the order; **ADMIN** may cancel any order
- Lifecycle states: `PENDING`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`

### Write-path correctness

- Idempotent create semantics:
  - `COMPLETED` key returns previously created order
  - `IN_PROGRESS` key prevents unsafe duplicate execution
  - completion is recorded after transaction commit
- Optimistic locking (`@Version`) for concurrent update/cancel safety
- Regional write gating (`RegionalFailoverManager`) blocks writes when node is passive

### Event-driven processing and scheduling

- `OrderCreated` events are persisted to outbox and published asynchronously
- Outbox pipeline:
  - `OutboxPublisher` (scheduler/backpressure)
  - `OutboxFetcher` (partition claim)
  - `OutboxProcessor` (async publish flow)
  - `OutboxRetryHandler` (retry/backoff policy)
- **Automatic `PENDING` → `PROCESSING`:** Spring `@Scheduled` in `PendingToProcessingScheduler` on a fixed interval (default **every five minutes**, `app.scheduling.pending-to-processing-ms`). **This is the only automatic promotion path.**
- Kafka consumer (`OrderCreatedConsumer`):
  - acknowledges `ORDER_CREATED` and writes **processed-event** dedupe markers (at-least-once, idempotent application)
  - does **not** change order status to `PROCESSING`
  - uses retry-topic semantics for transient failures and DLT handling for terminal failures

### Cache, rate limiting, and resilience

- Redis cache-aside with TTLs, jitter, and fail-soft behavior
- **Read cache keys are scoped** (`OrderReadCacheKeys`): admin global keys vs per-owner keys so the cache cannot serve one user another user’s order
- Redis token-bucket rate limiting via Lua script (distributed and atomic)
- Redis/Kafka/DB dependency health monitoring for multi-region failover mode
- Circuit-breaker style protections in Redis cache and Kafka producer paths

### Security and observability

- OAuth2/JWT resource server with role-based endpoint authorization (`ROLE_USER`, `ROLE_ADMIN` from JWT `roles` claim via `RoleClaimJwtAuthenticationConverter`)
- Global exception mapping to consistent `ApiError` responses (`403` **FORBIDDEN**, `404` **NOT_FOUND**, `409` **CONFLICT**, `503` **INFRASTRUCTURE_ERROR**; uncaught → `500` with **`api.errors.unexpected`** counter and error logging)
- Request/region context propagation (`X-Request-Id`, region tags)
- Prometheus metrics and OpenTelemetry tracing support

## Architecture Overview

```text
Client -> Interface HTTP -> Application Services -> Domain Aggregate/State
                                 |                 |
                                 v                 v
                          Ports (Repository/Cache/Event)
                                 |
                                 v
                    Infrastructure (JPA, Redis, Kafka, Resilience)
```

## Technology Stack

- **Language/Runtime**: Java 17
- **Framework**: Spring Boot 3
- **Web/API**: Spring Web, Jakarta Validation
- **Persistence**: Spring Data JPA (Hibernate), relational DB
- **Messaging**: Kafka, Spring Kafka
- **Caching/Rate Limiting**: Redis (Lettuce)
- **Security**: Spring Security OAuth2 Resource Server (JWT)
- **Observability**: Micrometer, Prometheus endpoint, OpenTelemetry tracing
- **Build/Test**: Maven, JUnit 5, Mockito, Spring test stack

## Repository Structure

```text
src/main/java/com/example/orderprocessing
  interfaces/http          # controllers, DTOs, API errors, exception mapping
  application              # command/query services, ports, events, exceptions
  domain/order             # aggregate, statuses, state implementations
  infrastructure           # persistence, messaging, cache, web, security, resilience
  config                   # runtime/security/redis configuration
docs                       # Jekyll site + detailed architecture and operational documentation
.github/workflows        # CI (test), Pages (doc deploy)
```

## API Endpoints (summary)

| Method | Path | Roles | Notes |
|--------|------|-------|--------|
| `POST` | `/orders` | USER, ADMIN | Optional `X-Idempotency-Key`; stores `sub` as owner |
| `GET` | `/orders/{id}` | USER, ADMIN | Scoped by owner unless ADMIN |
| `GET` | `/orders` | USER, ADMIN | List bounded; scoped by owner unless ADMIN |
| `GET` | `/orders/page` | USER, ADMIN | Paginated; same scoping as list |
| `PATCH` | `/orders/{id}/status` | ADMIN | Optimistic concurrency (`version`) |
| `PATCH` | `/orders/{id}/cancel` | USER, ADMIN | Domain: `PENDING` only; auth: owner or ADMIN |

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.9+
- Kafka broker (for full integration; tests can disable listeners)
- Redis instance (or sentinel/cluster as configured)

### Build and run

```bash
mvn clean compile
mvn test
mvn spring-boot:run
```

### Continuous integration

Workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs **`mvn -B clean test`** on:

- **push** to `main`, `master`, or `docs`
- **pull_request** targeting `main` or `master`

Uses JDK 17 (Temurin) with Maven dependency cache.

### Health and metrics

- Health: `/actuator/health`
- Prometheus: `/actuator/prometheus`

## Configuration Notes

Key runtime areas (see [Configuration and Runtime](docs/reference/configuration-and-runtime.md)):

- Outbox polling, partitioning, retry, and retention
- Scheduler cadence for `PENDING` → `PROCESSING` (`app.scheduling.pending-to-processing-ms`)
- Kafka topic, consumer group, consumer retry (`app.kafka.consumer-retry-*`), publisher circuit-breaker
- Cache TTL, **scoped read keys**, and Redis resilience controls
- Multi-region failover thresholds and idempotency TTL settings

## Testing and Quality

Coverage includes: domain transitions, idempotency lifecycle, outbox/messaging components, cache/rate-limit behavior, failover controls, API integration (**ownership on GET/list/cancel**, admin overrides), and CI on every mainline push/PR. See [Testing and Quality](docs/testing-and-quality.md) for gaps and expansion ideas.
