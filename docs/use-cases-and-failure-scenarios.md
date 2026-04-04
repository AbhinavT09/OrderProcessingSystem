# Use Cases and Failure Cases

This section documents practical end-to-end use cases for the Order Processing System.
Each use case has:

- **Positive path**: expected successful behavior
- **Failure path**: resilience/fallback behavior
- **Real example data**: request/event payloads and expected outcomes

---

## Use Case 1: Create Order (Idempotent Write)

### Positive case

Client sends:

```http
POST /orders
X-Idempotency-Key: checkout-789-user-42
Content-Type: application/json

{
  "items": [
    {"productName": "USB-C Cable", "quantity": 2, "price": 9.99},
    {"productName": "Power Adapter", "quantity": 1, "price": 19.99}
  ]
}
```

System behavior:

1. `OrderService` checks regional write policy (`RegionalFailoverManager`).
2. Global idempotency lock is attempted in Redis (`GlobalIdempotencyService`).
3. Order is persisted with status `PENDING`.
4. Outbox row is written in the same transaction.
5. Response returns `201 Created`.

Example response:

```json
{
  "id": "6a84f0a9-6ae9-49f8-9538-c5bb6cb4ac3d",
  "status": "PENDING",
  "items": [
    {"productName": "USB-C Cable", "quantity": 2, "price": 9.99},
    {"productName": "Power Adapter", "quantity": 1, "price": 19.99}
  ],
  "createdAt": "2026-04-04T12:10:15Z",
  "version": 0
}
```

### Failure case

Condition:

- Same idempotency key retried during network retry or region failover.

Expected behavior:

- If already completed, existing order is returned (no duplicate row).
- If in-flight lock exists, request fails with conflict message.

Example conflict response:

```json
{
  "code": "CONFLICT",
  "message": "Duplicate request in progress for idempotency key",
  "requestId": "4f0d6f02-8bfa-4f6f-b5f7-bbb62da2f847",
  "timestamp": "2026-04-04T12:10:18Z"
}
```

---

## Use Case 2: Outbox to Kafka Event Publish

### Positive case

Outbox row payload example:

```json
{
  "schemaVersion": 2,
  "eventId": "evt-10001",
  "eventType": "ORDER_CREATED",
  "orderId": "6a84f0a9-6ae9-49f8-9538-c5bb6cb4ac3d",
  "occurredAt": "2026-04-04T12:10:15Z"
}
```

Expected behavior:

1. `OutboxPublisher` claims rows with partition-aware `FOR UPDATE SKIP LOCKED`.
2. `VersionedJsonOrderCreatedEventSchemaRegistry` validates payload.
3. `KafkaEventPublisher` publishes with key `orderId`.
4. Row is marked `SENT`.

### Failure case

Condition:

- Kafka unavailable.

Expected behavior:

- Row transitions to `FAILED`.
- `retryCount` increments.
- `nextAttemptAt` uses exponential backoff.
- No event loss because data remains in DB.

---

## Use Case 3: Delayed Processing (`PENDING -> PROCESSING`)

### Positive case

Consumer receives an `ORDER_CREATED` event older than 5 minutes:

```json
{
  "schemaVersion": 2,
  "eventId": "evt-10001",
  "eventType": "ORDER_CREATED",
  "orderId": "6a84f0a9-6ae9-49f8-9538-c5bb6cb4ac3d",
  "occurredAt": "2026-04-04T12:10:15Z"
}
```

Expected behavior:

1. Dedupe check in `ProcessedEventRepository`.
2. Transition to `PROCESSING`.
3. Persist processed marker.
4. Ack offset manually.

### Failure case

Condition:

- Message arrives too early (before 5-minute delay) or transient DB failure.

Expected behavior:

- Throws retryable exception.
- Retries via topic/backoff policy.
- Routes to DLQ only after bounded attempts.

---

## Use Case 4: Read Order from Cache-Aside Layer

### Positive case

Request:

```http
GET /orders/6a84f0a9-6ae9-49f8-9538-c5bb6cb4ac3d
```

Expected behavior:

1. `OrderQueryService` checks Redis key `order:id:<uuid>`.
2. Cache hit returns quickly.
3. Metrics: `cache.hit.count++`.

### Failure case

Condition:

- Redis unavailable.

Expected behavior:

- `RedisCacheProvider` logs warning.
- Returns empty and falls back to DB.
- API still succeeds.
- `cache.error.count++`, `redis.connection.failures++`.

---

## Use Case 5: Distributed Rate Limiting

### Positive case

For user `u-42`, path `/orders`, IP `203.0.113.10`:

Redis key:

```text
rate_limit:u-42:/orders:203.0.113.10
```

Expected behavior:

- Token bucket Lua script allows within capacity.
- Request proceeds.
- `rate_limit.allowed.count++`.

### Failure case

Condition:

- Redis script execution fails.

Expected behavior:

- Fail-open path allows request.
- Warning log emitted.
- `redis.connection.failures{component=rate_limiter}++`.

---

## Use Case 6: Multi-Region Failover Write Control

### Positive case

Condition:

- Region healthy; mode is `active-passive:active`.

Expected behavior:

- Writes are allowed (`OrderService` continues).
- Region metrics/logs include `region_id`.

### Failure case

Condition:

- Health checks fail repeatedly (DB/Redis/Kafka) and threshold is reached.

Expected behavior:

1. `RegionalFailoverManager` changes node state to `passive`.
2. `failover.events.count++`.
3. Write APIs return service unavailable to prevent split-brain writes.

Example response:

```json
{
  "code": "INFRASTRUCTURE_ERROR",
  "message": "Upstream dependency unavailable",
  "requestId": "f7fe7b2a-6047-4d01-8f6a-d5f2a1419f8f",
  "timestamp": "2026-04-04T12:45:00Z"
}
```

---

## Use Case 7: Event Schema Evolution Compatibility

### Positive case (backward compatibility)

Older payload without `schemaVersion`:

```json
{
  "eventId": "evt-v1-200",
  "eventType": "ORDER_CREATED",
  "orderId": "ab9db4da-bf37-4ce5-9349-c6659d2d42d3",
  "occurredAt": "2026-04-04T08:00:00Z"
}
```

Expected behavior:

- Schema registry defaults to v1 parse rules.
- Required fields validated.
- Processing continues safely.

### Failure case

Condition:

- Missing required field (for example `orderId`).

Expected behavior:

- Validation rejects payload.
- `kafka.schema.validation.errors++`.
- Invalid event does not progress business state.

---

## Conflict Resolution and Consistency Notes

- Cross-region writes are designed with **eventual consistency** assumptions.
- Conflict resolution strategy is configurable (`app.multi-region.consistency.conflict-resolution`), default `last-write-wins`.
- Idempotency and optimistic locking together minimize duplicate effects and stale-write corruption.

