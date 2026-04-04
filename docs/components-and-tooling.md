# Components and Tooling Rationale

## Component-level design with real-world examples

### API boundary

- `OrderController` + DTOs isolate transport concerns from core logic.
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

## Tooling rationale (accurate to current implementation)

### Spring Boot
- Provides app lifecycle, dependency injection, actuator, and convention-driven wiring.

### Spring Web
- Declarative REST APIs and request validation integration.

### Spring Data JPA + Hibernate
- Entity persistence, custom repository methods, optimistic locking.

### Spring Data Redis (Lettuce)
- Distributed cache and distributed rate limiter state store.

### Kafka + Spring Kafka
- Durable async event pipeline, consumer groups, retry topics, DLQ.

### Spring Retry
- Complements transient failure handling patterns.

### Spring Security + OAuth2 Resource Server
- JWT validation, role mapping, endpoint authorization.

### Micrometer + Prometheus
- Unified instrumentation for business + infra metrics.

### OpenTelemetry (Micrometer bridge)
- Trace export to OTLP endpoint for distributed diagnostics.

### Maven + Surefire + JUnit 5
- Reproducible build/test lifecycle and layered testing.

## Failure-case design by tool

### Redis cache failure
- Tool behavior: exceptions caught inside `RedisCacheProvider`.
- Outcome: fallback to DB read path.

### Redis rate limiter failure
- Tool behavior: limiter fail-open with warning log.
- Outcome: availability preserved, but throttling temporarily relaxed.

### Kafka producer failure
- Tool behavior: outbox keeps event pending/failed with retries.
- Outcome: eventual publish once dependency recovers.

### Kafka consumer duplicate delivery
- Tool behavior: processed-event dedupe check first.
- Outcome: duplicate message skipped safely.

### DB optimistic lock conflict
- Tool behavior: version mismatch triggers conflict exception.
- Outcome: API returns conflict, client retries with fresh state.

