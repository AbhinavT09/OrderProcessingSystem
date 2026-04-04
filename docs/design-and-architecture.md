# Design and Architecture

## 1) Architectural style

The project uses **Hexagonal Architecture + CQRS + DDD Aggregate**:

- **Domain layer:** business invariants and state transitions only
- **Application layer:** use-case orchestration and ports
- **Infrastructure layer:** Kafka, Redis, persistence, filters, security adapters
- **API layer:** HTTP transport + DTO + exception mapping

CQRS split:

- **Write:** `OrderService`
- **Read:** `OrderQueryService`

## 2) Why this design (real-world reasoning)

- In a real e-commerce checkout system, you need **strict write correctness** (no duplicate order creation, no lost updates) and **fast reads** (cache backed).
- Messaging is asynchronous because payment/fulfillment style flows are not always immediate.
- Outbox is used to prevent the "DB commit succeeded but Kafka failed" inconsistency.

## 3) Domain model and invariants

### Aggregate root: `Order`

`Order` encapsulates lifecycle behavior:

- `updateStatus(target)`
- `cancel()`
- `promotePendingToProcessing()`

### State Pattern

`OrderState` implementations enforce legal transitions:

- `PendingState`
- `ProcessingState`
- `ShippedState`
- `DeliveredState`
- `CancelledState`

Invalid transitions throw `ConflictException`.

## 4) Consistency model

### Strong consistency

- Order writes in DB transaction
- Optimistic locking (`@Version`) for update/cancel races
- Idempotent order creation with unique idempotency key

### Eventual consistency

- `PENDING -> PROCESSING` is asynchronous via Kafka consumer delay
- Retries + DLQ for transient/non-transient failures

## 5) Transactional Outbox implementation

### Write transaction flow

1. Persist `OrderEntity`
2. Persist `OutboxEntity(status=PENDING, payload=OrderCreatedEvent)`
3. Commit transaction

No Kafka call occurs inside this transaction.

### Publisher flow

`OutboxPublisher` periodically scans `PENDING/FAILED` rows and publishes:

- On success -> status `SENT`
- On failure -> `retryCount++`, status `FAILED`
- Exponential backoff and max retries prevent infinite loops

## 6) Kafka consumer reliability design

`OrderCreatedConsumer` correctness controls:

- Checks `ProcessedEventRepository` **before** processing
- Persists processed marker **after** successful processing
- Manual ack mode (`manual_immediate`) and ack only on success/duplicate-safe path
- Retry-topic based delayed retries; bounded attempts
- DLQ handler with rich context logging (topic/partition/offset/event ids)
- Rebalance lifecycle visibility via `KafkaConsumerRebalanceObserver`

## 7) Distributed Redis design

### Cache

- `RedisCacheProvider` uses JSON serialization
- TTL strategy:
  - order by id = 5 min
  - order list = 1 min
- Fail-safe behavior: cache failures log and fallback to DB path
- Stampede protection retained in `OrderQueryService` with key locks

### Rate limiting

- `RateLimitingFilter` uses Redis Lua token bucket for atomic updates
- Key format: `userId:path:ip`
- Works across multiple service instances
- Atomic operation prevents races under concurrency

## 8) Security architecture

- JWT stateless auth
- `roles` claim -> `ROLE_*` authorities
- Endpoint-level RBAC in `SecurityConfig`
- Request correlation (`request_id`) and API error normalization

## 9) Real-world failure case walkthroughs

### Case A: Kafka publish outage during order creation

- Order DB save succeeds
- Outbox row remains `PENDING`
- OutboxPublisher retries until Kafka healthy
- No event lost; no transaction rollback needed

### Case B: Duplicate Kafka message delivery

- Consumer checks processed-event table first
- Duplicate message is skipped and acked
- No duplicate state transition

### Case C: Redis unavailable

- Cache get/put/evict logs warning and degrades gracefully
- Read path falls back to DB and continues serving
- Rate limiter fails open to preserve API availability

### Case D: Concurrent status updates

- Stale version update hits optimistic locking mismatch
- Service returns `409 CONFLICT`
- No lost update in DB

### Case E: Consumer rebalance during processing

- Rebalance lifecycle logs are emitted
- Manual ack only after successful processing
- Unacked records can be reassigned/retried safely

## 10) Architecture-level failure matrix

| Failure | Protection | Result |
|---|---|---|
| DB save fails in create | transaction rollback | no order, no outbox |
| Kafka unavailable at publish time | outbox retry/backoff | eventual publish |
| Duplicate Kafka record | processed-event dedupe | idempotent consume |
| Redis cache failure | fail-safe cache provider | DB fallback |
| Rate limiter Redis failure | fail-open + log | availability preserved |
| Concurrent updates | optimistic lock + version check | conflict response |
| Retry storm risk | bounded retry attempts + DLQ | no infinite loops |

