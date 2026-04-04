# Observability, Logging, Monitoring, and Operations

## 1) Logging implementation details

Structured JSON logging includes:

- `timestamp`, `level`, `logger`, `message`
- `request_id` from `RequestContextFilter`
- `order_id` from service-level MDC context
- `trace_id` from tracing context

`X-Request-Id` is accepted/generated and returned to clients, enabling incident correlation.

## 2) Metrics catalog (current implementation)

### HTTP and API metrics

- `http.server.request.count`
- `http.server.request.latency` (p95, p99)
- `http.server.request.errors`

### Business/service metrics

- `orders.service.request.count`
- `orders.created.count`
- `orders.idempotency.hit.count`
- `orders.operation.failure.count`
- `orders.operation.duration`
- `orders.query.request.count`
- `orders.query.error.count`
- `orders.query.duration`

### Cache metrics

- `cache.hit.count`
- `cache.miss.count`
- `cache.error.count`

### Rate limiting metrics

- `rate_limit.allowed.count`
- `rate_limit.blocked.count`

### Outbox metrics

- `outbox.pending.count`
- `outbox.failure.count`
- `outbox.publish.latency`

### Kafka consumer metrics

- `kafka.consumer.errors`
- `kafka.consumer.lag.ms`
- `kafka.consumer.processed.count`
- `kafka.consumer.retry.count`
- `kafka.consumer.dlq.count`

## 3) Tracing

Configured through Micrometer bridge and OTLP endpoint:

- `management.tracing.sampling.probability`
- `management.otlp.tracing.endpoint`

Use traces + request_id for end-to-end debugging.

## 4) Reliability and retry behavior

### OutboxPublisher

- Polls pending/failed rows
- Exponential backoff based on retry count
- Bounded retries (max retries)
- `SENT` / `FAILED` state transitions tracked in DB

### Kafka consumer

- Manual ack mode (`manual_immediate`)
- Ack only after safe completion
- Retry topics with exponential backoff and max attempts
- DLQ handler for terminal failures

## 5) DLQ operations guidance

When `kafka.consumer.dlq.count` rises:

1. Inspect DLQ logs with `eventId`, `orderId`, topic/partition/offset.
2. Classify failures:
   - payload/schema issues
   - missing order records
   - persistent downstream failures
3. Decide replay/ignore policy.
4. Apply fix and replay if safe.

## 6) Real-world monitoring scenarios

### Scenario: Kafka outage

Symptoms:

- rising `outbox.pending.count`
- rising `outbox.failure.count`
- stable API latency (writes still commit)

Action:

- restore Kafka
- confirm outbox drain by falling pending/failure gauges

### Scenario: Redis instability

Symptoms:

- rising `cache.error.count`
- possibly rising DB read load

Action:

- restore Redis
- verify cache hits recover

### Scenario: abusive traffic or bot spikes

Symptoms:

- rising `rate_limit.blocked.count`

Action:

- tune token bucket limits/window
- add edge/WAF controls if needed

### Scenario: consumer retry storm

Symptoms:

- rising `kafka.consumer.retry.count`
- lag rising
- potential DLQ increase

Action:

- inspect root cause class of retries
- verify retry bounds and delay settings
- scale consumers / fix downstream dependency

## 7) Runbook checkpoints

- Verify Kafka connectivity and topic health.
- Verify Redis availability and latency.
- Verify Prometheus scraping `/actuator/prometheus`.
- Verify trace export endpoint and sampling settings.
- Verify outbox backlog and DLQ trends on deployments.

