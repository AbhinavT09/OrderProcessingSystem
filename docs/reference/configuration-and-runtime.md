# Configuration and Runtime Controls

## Configuration Package Layout

- `config/security/SecurityConfig`
  - JWT resource server
  - route authorization rules
  - authentication/authorization error responses
- `config/redis/RedisLettuceConfig`
  - Redis/Lettuce client setup
  - timeout/reconnect/pooling tuning
- `config/kafka`
  - package reserved for Kafka-focused runtime wiring
- `config/observability`
  - package reserved for observability-focused runtime wiring

## `application.yml` Controls

### Outbox and publisher controls

- `app.outbox.max-retries`
- `app.outbox.backoff-base-ms`
- `app.outbox.partition.total`
- `app.outbox.partition.instance-id`
- `app.outbox.partition.instance-count`
- `app.outbox.publisher.poll-ms`
- `app.outbox.publisher.batch-size`
- `app.outbox.publisher.parallelism`
- `app.outbox.publisher.max-in-flight`
- `app.outbox.publisher.slow-kafka-threshold-ms`
- `app.outbox.cleanup.retention-days`
- `app.outbox.cleanup.poll-ms`

### Kafka publish and consumer controls

- `app.kafka.order-events-topic`
- `app.kafka.consumer-group`
- `app.kafka.processing-delay-ms`
- `app.kafka.processing-delay-multiplier`
- `app.kafka.processing-delay-max-ms`
- `app.kafka.delayed-processing-attempts`
- `app.kafka.publisher.circuit-breaker.failure-threshold`
- `app.kafka.publisher.circuit-breaker.open-seconds`

### Multi-region and idempotency controls

- `app.multi-region.enabled`
- `app.multi-region.region-id`
- `app.multi-region.failover.mode`
- `app.multi-region.failover.rto-seconds`
- `app.multi-region.failover.rpo-seconds`
- `app.multi-region.auto-failover.poll-ms`
- `app.multi-region.auto-failover.unhealthy-threshold`
- `app.multi-region.auto-failover.healthy-threshold`
- `app.multi-region.health.check-timeout-ms`
- `app.multi-region.health.redis-latency-threshold-ms`
- `app.multi-region.health.db-failure-threshold`
- `app.multi-region.health.redis-failure-threshold`
- `app.multi-region.health.kafka-failure-threshold`
- `app.multi-region.global-idempotency.lock-ttl-seconds`
- `app.multi-region.global-idempotency.completed-ttl-seconds`

## Operationally Important Runtime Flags

- idempotency header remains optional; behavior changes when key absent/present
- passive mode blocks writes but does not block reads
- outbox retry ceiling and backoff strongly influence event freshness
- delayed consumer processing settings influence order progression latency

## Metrics Touchpoints

- Outbox: `outbox.pending.count`, `outbox.failure.count`, `outbox.batch.size`, `outbox.publish.latency`, `outbox.publish.rate`, `outbox.lag`, `outbox.retry.count`.
- Kafka: `kafka.consumer.errors`, `kafka.consumer.processed.count`, `kafka.consumer.retry.count`, `kafka.consumer.dlq.count`, `kafka.consumer.lag.ms`, `kafka.schema.validation.errors`, `kafka.event.version.distribution`.
- Regional resilience: `failover.events.count`, `region.health.unhealthy.count`, `region.health.dependency.failure.count`, `region.health.redis.slow.count`, `region.health.redis.latency`.
- Cache and rate limiting: `cache.hit.count`, `cache.miss.count`, `cache.error.count`, `cache.degraded.mode.count`, `rate_limit.allowed.count`, `rate_limit.blocked.count`.
