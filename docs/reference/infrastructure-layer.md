# Infrastructure Layer Reference

## Messaging and outbox pipeline

### Outbox component split
- `OutboxPublisher`: scheduler/coordinator; computes owned partitions, enforces max in-flight semaphore, dispatches worker tasks, refreshes pending/failure gauges, archives old `SENT` records.
- `OutboxFetcher`: transactional row claiming (`claimBatchForPartition`), deterministic in-partition ordering by aggregate then created time, batch-size metric recording.
- `OutboxProcessor`: async publish orchestration; sets in-flight lease by moving record to `FAILED` + `nextAttemptAt`, triggers publish future, marks `SENT` on completion, records latency/rate/lag metrics.
- `OutboxRetryHandler`: retry counter, exponential backoff with cap, terminal handling when max retries reached.

### Kafka async publisher and circuit-breaker
- `KafkaEventPublisher` validates/serializes through `OrderCreatedEventSchemaRegistry` before send.
- Per-order key serialization is preserved via `keyPublishChains` map to avoid reordering for same aggregate key.
- Publisher path is non-blocking callback-driven; it does not perform local blocking waits.
- Retry ownership is centralized in outbox (`OutboxRetryHandler`), not in `KafkaEventPublisher`.
- Circuit opens after `failure-threshold` consecutive send failures; open window controlled by `open-seconds`; publishes fail fast while open.

### Consumer transaction boundary hardening
- `OrderCreatedConsumer.consume(...)` parses event, enforces delayed-processing window, then calls `processEventInTransaction(...)`.
- Dedupe guard (`processed_events`) and state transition update run inside transaction template; processed marker is persisted in same transactional boundary.
- Manual acknowledgment occurs only after successful transaction path or duplicate-safe integrity-race handling.
- `@RetryableTopic` includes delayed and retryable exception categories; DLT path logs rich context.

## Resilience and multi-region controls

### `RegionalFailoverManager`
- Tracks separate failure counters for DB, Redis, and Kafka plus global unhealthy/healthy streaks.
- Applies dependency-specific thresholds and global threshold before switching to passive in active-passive mode.
- Hardening checks:
  - DB: `Connection.isValid(timeout)`.
  - Redis: ping plus latency timer and slow-check counter thresholding.
  - Kafka: cluster metadata, controller presence, node count, and topic listing check.
- Emits failover metrics (`failover.events.count`, dependency failure counters, unhealthy counters).

### Supporting components
- `MultiRegionHealthIndicator`: surfaces active/passive mode and write-allowed state.
- `GlobalIdempotencyService`: redis lock acquisition and completed-order mapping for cross-region retries.

## Persistence adapters/entities
- Repository adapters: `PostgresOrderRepository`, `JpaOutboxRepository`, `JpaProcessedEventRepository`.
- Spring repositories: `SpringOrderJpaRepository`, `SpringOutboxJpaRepository`, `SpringOutboxArchiveJpaRepository`, `SpringProcessedEventJpaRepository`.
- Core entities: `OrderEntity`, `OrderItemEmbeddable`, `OutboxEntity`, `OutboxArchiveEntity`, `OutboxStatus`, `ProcessedEventEntity`.

## Web/security infrastructure
- `RequestContextFilter`: request/region context propagation in MDC and regional request metrics.
- `RateLimitingFilter`: Redis Lua token bucket keyed by user/path/ip with fail-open behavior.
- `SecurityConfig` + `RoleClaimJwtAuthenticationConverter`: JWT auth, role mapping, route RBAC, JSON 401/403 responses.
