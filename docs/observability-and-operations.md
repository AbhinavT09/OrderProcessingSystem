# Observability, Logging, Monitoring, and Operations

## Observability model

The system implements all three observability pillars:

- **Logs:** structured JSON logs with correlation fields
- **Metrics:** Micrometer counters/timers/summaries exposed to Prometheus
- **Traces:** OTEL-based distributed tracing export

## Structured logging details

Configured in `application.yml` via `logging.pattern.console` to output JSON lines including:

- `timestamp`
- `level`
- `logger`
- `message`
- `request_id` (MDC)
- `order_id` (MDC)
- `trace_id` (MDC trace context)

### Correlation fields

- `request_id`
  - Set by `RequestContextFilter`
  - Returned in `X-Request-Id` response header
- `order_id`
  - Set/cleared in write operations in `OrderService`
- `trace_id`
  - Provided by tracing bridge when present

## Metrics inventory

### HTTP edge metrics

From `RequestContextFilter`:

- `http.server.request.count`
  - tags: `method`, `path`, `status`
- `http.server.request.latency`
  - with p95/p99 publication
- `http.server.request.errors`
  - increments for `>=500`

### Application metrics

From write/read services:

- `orders.service.request.count`
- `orders.created.count`
- `orders.idempotency.hit.count`
- `orders.operation.failure.count`
- `orders.operation.duration` (p95/p99)
- `orders.query.request.count`
- `orders.query.error.count`
- `orders.query.duration` (p95/p99)

### Messaging metrics

From Kafka consumer and rate limiter:

- `kafka.consumer.errors`
- `kafka.consumer.lag.ms` (distribution summary, p95/p99)
- `http.server.rate_limit.blocked.count`

## Tracing details

Configured in `application.yml`:

- `management.tracing.sampling.probability: 1.0`
- `management.otlp.tracing.endpoint: http://localhost:4318/v1/traces`

This enables trace export through the Micrometer bridge to OTLP.

## Runtime resilience mechanisms

### Producer resilience (`KafkaEventPublisher`)

- Retries synchronous send with exponential backoff.
- Opens local circuit after configured consecutive failures.
- Resets circuit after configured cool-down.
- Throws `InfrastructureException` when publish fails.

### Consumer resilience (`OrderCreatedConsumer`)

- Retryable topic strategy with exponential backoff.
- Delayed processing model:
  - if event is younger than delay window, throw `DelayedProcessingNotReadyException` to trigger retry later.
- Wraps transient DB errors as `RetryableProcessingException`.
- Emits DLT logs through `@DltHandler`.

### Cache resilience (`RedisCacheProvider`)

- `get/put/evict` operations catch runtime cache exceptions.
- Cache failures degrade to DB-backed behavior instead of failing request.

### Data integrity resilience

- Idempotent create with unique idempotency key.
- Optimistic locking conflict handling on updates/cancel.
- Event processing dedupe via `processed_events` unique key.

## Security and edge protections

- JWT authentication and role-based authorization (`SecurityConfig`)
- Validation at DTO/controller boundary
- Rate limiting with fixed window + per-route/per-subject keying

## Operations checklist

- Ensure Kafka is reachable at configured bootstrap server.
- Set strong `JWT_SECRET` in production.
- Scrape `/actuator/prometheus` from Prometheus.
- Export traces to OTEL collector endpoint.
- Monitor key signals:
  - rising `kafka.consumer.errors`
  - rising `orders.operation.failure.count`
  - high `kafka.consumer.lag.ms`
  - high `http.server.rate_limit.blocked.count`
