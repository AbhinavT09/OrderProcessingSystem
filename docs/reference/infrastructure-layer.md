# Infrastructure Layer Reference

## Scope

Infrastructure implements application ports and hosts all external-system integrations (DB, Kafka, Redis, security, regional health).

## Package Map

- `infrastructure/persistence`
  - `adapter`: port implementations over Spring Data repositories
  - `entity`: JPA persistence models and lifecycle hooks
  - `repository`: Spring Data interfaces and custom queries
- `infrastructure/messaging`
  - outbox orchestration and shared messaging exceptions/utilities
- `infrastructure/messaging/producer`
  - Kafka producer adapters
- `infrastructure/messaging/consumer`
  - Kafka listeners, retry-topic integration, DLT handling
- `infrastructure/messaging/schema`
  - event schema validation and compatibility rules
- `infrastructure/cache`
  - Redis-backed cache adapter with degrade-safe behavior
- `infrastructure/web`
  - request context and distributed rate limiting filters
- `infrastructure/security`
  - JWT claim-to-authority conversion helpers
- `infrastructure/crosscutting`
  - global idempotency state coordinator
- `infrastructure/resilience`
  - regional health management and health indicators

## Messaging and Outbox

### Package structure

- `infrastructure/messaging`
- `infrastructure/messaging/producer`
- `infrastructure/messaging/consumer`
- `infrastructure/messaging/schema`

### Outbox pipeline

- `OutboxPublisher`
  - scheduled coordinator
  - partition worker dispatch
  - semaphore-based in-flight backpressure
  - sent-row archive cleanup scheduling
- `OutboxFetcher`
  - partition-aware transactional claim
  - ordered candidate retrieval
- `OutboxProcessor`
  - async publication orchestration
  - success transition to `SENT`
  - publish latency/rate/lag metrics
- `OutboxRetryHandler`
  - retry count management
  - bounded backoff and terminal handling

### Outbox state model (`OutboxStatus`)

- `PENDING`: ready for first publication
- `FAILED`: publish attempt failed or leased in-flight; eligible for retry by `nextAttemptAt`
- `SENT`: publication completed successfully; eligible for archival cleanup

### Claiming and concurrency controls

- partition-aware claim prevents worker overlap across partitions
- skip-locked SQL claim avoids duplicate leasing under concurrent schedulers
- semaphore in publisher limits global in-flight worker pressure
- batch-size controls enforce predictable per-poll work bounds

### Producer path (`KafkaEventPublisher`)

- validates/serializes events through schema registry
- preserves per-order key ordering via chained futures
- applies circuit-breaker fail-fast behavior after repeated failures
- delegates retries to outbox retry policy

#### Producer failure semantics

- broker send failure is surfaced as exceptional completion
- repeated failures open circuit for cooldown period
- while open, send attempts fail fast and rely on outbox retry windows

### Consumer path (`OrderCreatedConsumer`)

- parses and validates payload
- enforces delayed-processing window
- executes dedupe + transition + marker write transactionally
- uses retry-topic and DLT for failure containment

#### Consumer idempotency and safety

- `processed_events` table guards duplicate deliveries
- duplicate marker race on unique constraint is handled safely and acknowledged
- manual acknowledgment occurs only after successful or duplicate-safe processing path

## Resilience and Crosscutting Controls

### `RegionalFailoverManager`

- dependency probes for DB, Redis, Kafka
- threshold-based ACTIVE/PASSIVE switching
- recovery threshold before returning to ACTIVE
- write gating hook consumed by application write service

#### Health check strategy

- DB: JDBC connection validity check within timeout
- Redis: ping + latency threshold tracking
- Kafka: cluster metadata + controller + topic-listing validation

### `GlobalIdempotencyService`

- Redis-backed request-key lifecycle state:
  - `IN_PROGRESS`
  - `COMPLETED`
- compatibility parsing for legacy key encodings
- completion mapping used to prevent duplicate creates

### `MultiRegionHealthIndicator`

- exposes current mode and write-eligibility via actuator health

## Persistence Infrastructure

### Adapter responsibilities

- `PostgresOrderRepository`
  - implements `OrderRepository` port
  - find/save operations for order read/write paths
- `JpaOutboxRepository`
  - implements `OutboxRepository`
  - supports save, partition claim, status counts, archive copy, and active-row cleanup
- `JpaProcessedEventRepository`
  - implements `ProcessedEventRepository`
  - dedupe marker existence checks and saves

### Entity intent

- `OrderEntity`
  - durable order snapshot
  - optimistic lock version
  - idempotency key persistence
- `OutboxEntity`
  - active outbox row including retry and scheduling metadata
  - lifecycle hooks initialize defaults and timestamps
- `OutboxArchiveEntity`
  - immutable historical copy of sent events
- `ProcessedEventEntity`
  - consumed-event dedupe marker for idempotent consumers

## Persistence Infrastructure

### Adapters

- `PostgresOrderRepository`
- `JpaOutboxRepository`
- `JpaProcessedEventRepository`

### Spring Data repositories

- `SpringOrderJpaRepository`
- `SpringOutboxJpaRepository`
- `SpringOutboxArchiveJpaRepository`
- `SpringProcessedEventJpaRepository`

### Entities

- `OrderEntity`, `OrderItemEmbeddable`
- `OutboxEntity`, `OutboxArchiveEntity`, `OutboxStatus`
- `ProcessedEventEntity`

## Web and Security Infrastructure

- `RequestContextFilter`: request and region context propagation into MDC/response/metrics
- `RateLimitingFilter`: distributed Redis token bucket with fail-open policy
- `RoleClaimJwtAuthenticationConverter`: JWT role claim to authority mapping

## Key Metrics by Infrastructure Area

- **Outbox**
  - `outbox.pending.count`, `outbox.failure.count`, `outbox.publish.rate`, `outbox.publish.latency`, `outbox.lag`
- **Kafka consumer**
  - `kafka.consumer.processed.count`, `kafka.consumer.retry.count`, `kafka.consumer.errors`, `kafka.consumer.dlq.count`
- **Schema**
  - `kafka.schema.validation.errors`, `kafka.event.version.distribution`
- **Cache/Redis**
  - `cache.hit.count`, `cache.miss.count`, `cache.error.count`, `cache.degraded.mode.count`, `redis.connection.failures`
- **Rate limiting**
  - `rate_limit.allowed.count`, `rate_limit.blocked.count`
- **Regional failover**
  - `failover.events.count`, `region.health.unhealthy.count`, `region.health.dependency.failure.count`

## Infrastructure Failure Matrix

| Area | Failure | Protective behavior | Outcome |
|---|---|---|---|
| Outbox publish | Kafka unavailable | retry/backoff + failed status | eventual publication after recovery |
| Consumer | duplicate delivery | processed-event dedupe | no duplicate state mutation |
| Cache | Redis failure | fail-soft read path | DB fallback, higher latency possible |
| Rate limiting | Redis script error | fail-open policy | availability prioritized over throttling |
| Regional control | repeated dependency failure | switch to passive | writes gated until healthy |
