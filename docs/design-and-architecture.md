# Design and Architecture

## 1. Architectural Principles

The service is implemented with **Hexagonal Architecture**, **CQRS**, and **DDD aggregate modeling**.

- **Interface layer (`interfaces/http`)**: transport contracts, validation, error mapping
- **Application layer (`application/*`)**: command/query orchestration, ports, transactional boundaries
- **Domain layer (`domain/order/*`)**: aggregate behavior and state transition invariants
- **Infrastructure layer (`infrastructure/*`)**: persistence, messaging, cache, security, resilience, crosscutting controls

CQRS split:

- **Write side**: `OrderService`
- **Read side**: `OrderQueryService`

## 2. Core Design Decisions

### 2.1 Transactional Outbox over direct Kafka publish

Reason: commit order state and integration event atomically from the write transaction perspective.

- Order write and outbox row are persisted in one DB transaction
- Kafka publication is handled asynchronously by outbox workers
- Prevents "DB committed, broker send failed" inconsistency

### 2.2 State pattern for order lifecycle

Reason: enforce legal transitions in domain code, not controller/service condition chains.

- `Order` delegates behavior to `OrderState`
- Concrete states: `PendingState`, `ProcessingState`, `ShippedState`, `DeliveredState`, `CancelledState`
- Illegal transitions fail with `ConflictException`

### 2.3 Global idempotency lifecycle states

Reason: avoid false completion and data loss during partial failures.

- `IN_PROGRESS`: request accepted, business write not yet finalized
- `COMPLETED`: order commit succeeded and key is mapped to `orderId`
- completion is marked after commit via transaction synchronization

### 2.4 Active/passive write gating

Reason: prevent unsafe writes during regional dependency instability.

- `RegionalFailoverManager` monitors DB/Redis/Kafka health
- sustained unhealthy thresholds move region to passive
- write APIs are gated via `allowsWrites()`

## 3. Consistency Model

### Strong consistency paths

- `OrderEntity` writes inside transaction boundaries
- optimistic locking on updates/cancel (`@Version`)
- idempotent create semantics with durable key lifecycle

### Eventual consistency paths

- post-create progression to `PROCESSING` via Kafka consumer
- outbox retries/backoff for asynchronous publication
- retry topics and DLT for consumer-side failure isolation

## 4. Write Path Architecture

### 4.1 Create order flow

1. Validate request and authorize endpoint
2. Check regional write mode
3. Resolve idempotency state
4. Persist order + outbox event in one transaction
5. Return response
6. Mark idempotency key `COMPLETED` after commit

### 4.2 Update and cancel flow

- retrieve aggregate snapshot
- apply state transition/cancel behavior in domain model
- persist with optimistic version checks
- evict query cache entries

## 5. Outbox Publication Pipeline

Components:

- `OutboxPublisher`: scheduler + backpressure + worker orchestration
- `OutboxFetcher`: partition-aware due row claim
- `OutboxProcessor`: async publish + success transition
- `OutboxRetryHandler`: retry policy and next-attempt scheduling

Behavioral properties:

- bounded in-flight workers
- bounded batch claim size
- deterministic row ordering within processing batch
- at-least-once delivery with consumer dedupe

## 6. Kafka Consume and Dedupe Architecture

`OrderCreatedConsumer` provides:

- schema validation/deserialization
- delayed processing enforcement window
- transactional dedupe marker + state transition
- retry topic routing for transient conditions
- DLT handling with structured diagnostics

`processed_events` table is the idempotent-consume anchor.

## 7. Redis Usage Model

### Query cache

- adapter: `RedisCacheProvider`
- cache-aside read strategy
- soft-fail fallback to DB
- local circuit-breaker behavior during repeated Redis faults
- TTL jitter for synchronized-expiry reduction

### Rate limiting

- filter: `RateLimitingFilter`
- token bucket in Lua for atomic refill/consume
- distributed key scope (`user:path:ip`)
- fail-open during Redis degradation to preserve availability

## 8. Security Model

- resource server JWT validation
- role claim conversion to Spring authorities
- endpoint-level access controls in `SecurityConfig`
- normalized API error envelope with correlation id

## 9. Failure Matrix

| Failure mode | Defensive mechanism | Observable outcome |
|---|---|---|
| create transaction fails | transactional rollback | no order, no outbox row |
| broker unavailable | outbox retries/backoff | pending/failed backlog grows then drains |
| duplicate Kafka delivery | processed-event dedupe | idempotent consume, no repeated transition |
| Redis cache failure | fail-soft cache adapter | DB fallback, increased cache error metrics |
| rate limiter Redis failure | fail-open policy | API remains available |
| stale concurrent write | optimistic locking | conflict response |
| prolonged dependency outage | active/passive failover | writes rejected in passive region |

