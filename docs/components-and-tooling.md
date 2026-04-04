# Components and Tooling Rationale

## Component-level design with real-world examples

### Interface HTTP boundary

- `interfaces/http` (`OrderController` + DTOs) isolates transport concerns from core logic.
- `GlobalExceptionHandler` ensures stable error contract regardless of internal exception source.

Real-world example:

- Mobile app sends malformed JSON -> API returns deterministic `MALFORMED_REQUEST` instead of raw stack traces.

### Write side (`OrderService` + `OutboxService`)

- Handles strict write correctness and side effects separation.
- Outbox record is persisted in same transaction as order write.

Real-world example:

- Kafka cluster has transient outage at order time: customer still gets `201`, and outbox retries publish later.

### Read side (`OrderQueryService` + cache)

- Cache-aside with TTL to optimize hot reads.
- Coalescing lock prevents stampede on cache misses.

Real-world example:

- Flash-sale product causes thousands of reads for same order status -> one DB read fills cache, others reuse it.

### Async event processing (`OrderCreatedConsumer`)

- Delayed transitions with retry-topic model.
- Idempotent consume + manual ack only after success.

Real-world example:

- Consumer crashes after processing but before offset commit on one instance; re-delivery occurs, dedupe check prevents double-processing.

### Schema evolution layer (`VersionedJsonOrderCreatedEventSchemaRegistry`)

- Central contract authority for event shape/validation.
- Ensures producer and consumer use the same compatibility rules.

Real-world example:

- Region A deploys with schema v2 while Region B still processes v1 payloads; events continue to parse because `schemaVersion` is backward/forward compatible by design.

### Multi-region resilience layer (`RegionalFailoverManager` + `GlobalIdempotencyService`)

- Failover manager monitors DB/Redis/Kafka health and toggles write mode in active-passive setups.
- Global idempotency lock/resolution reduces duplicate creates when traffic shifts regions.

Real-world example:

- During DNS failover, client retries same create request to secondary region. Global idempotency mapping returns original `orderId` instead of creating a duplicate.

## Tooling rationale (accurate to current implementation)

### Spring Boot
- Provides app lifecycle, dependency injection, actuator, and convention-driven wiring.

### Spring Web
- Declarative REST APIs and request validation integration.

### Spring Data JPA + Hibernate
- Entity persistence, custom repository methods, optimistic locking.

### Spring Data Redis (Lettuce)
- Distributed cache and distributed rate limiter state store.
- HA options: cluster/sentinel, reconnect, pool tuning, and timeout controls.

### Kafka + Spring Kafka
- Durable async event pipeline, consumer groups, retry topics, DLQ.
- Supports schema-versioned JSON event contracts through a dedicated registry abstraction.

### Spring Retry
- Complements transient failure handling patterns.

### Spring Security + OAuth2 Resource Server
- JWT validation, role mapping, endpoint authorization.

### Micrometer + Prometheus
- Unified instrumentation for business + infra metrics.
- Tracks schema validation, version distribution, regional traffic, and failover events.

### OpenTelemetry (Micrometer bridge)
- Trace export to OTLP endpoint for distributed diagnostics.

### Maven + Surefire + JUnit 5
- Reproducible build/test lifecycle and layered testing.

## Failure-case design by tool

### Redis cache failure
- Tool behavior: exceptions caught inside `RedisCacheProvider`.
- Outcome: fallback to DB read path.
- Additional behavior: circuit breaker opens after repeated failures to reduce hot-path latency impact.

### Redis rate limiter failure
- Tool behavior: limiter fail-open with warning log.
- Outcome: availability preserved, but throttling temporarily relaxed.

### Kafka producer failure
- Tool behavior: `KafkaEventPublisher` performs async single-attempt send and surfaces failure to outbox; outbox keeps event pending/failed with retries.
- Outcome: eventual publish once dependency recovers.

### Kafka consumer duplicate delivery
- Tool behavior: processed-event dedupe check first.
- Outcome: duplicate message skipped safely.

### Schema validation failure
- Tool behavior: registry rejects invalid payload and increments `kafka.schema.validation.errors`.
- Outcome: bad event does not mutate domain state.

### Regional failover
- Tool behavior: failover manager transitions node to passive on sustained dependency failure.
- Outcome: write APIs are blocked until region health is restored.

### DB optimistic lock conflict
- Tool behavior: version mismatch triggers conflict exception.
- Outcome: API returns conflict, client retries with fresh state.

