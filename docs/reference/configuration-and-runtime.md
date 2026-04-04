# Configuration and Runtime Controls

## Configuration package layout

- `config/security/SecurityConfig` - JWT resource server, RBAC routes, JSON auth error handlers.
- `config/redis/RedisLettuceConfig` - Lettuce client timeout/reconnect tuning.
- `config/kafka` - reserved package for Kafka-specific configuration classes.
- `config/observability` - reserved package for observability-specific configuration classes.

## `application.yml` controls

### Outbox and publisher
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

### Kafka async publish and delayed consume
- `app.kafka.order-events-topic`
- `app.kafka.consumer-group`
- `app.kafka.processing-delay-ms`
- `app.kafka.processing-delay-multiplier`
- `app.kafka.processing-delay-max-ms`
- `app.kafka.delayed-processing-attempts`
- `app.kafka.publisher.circuit-breaker.failure-threshold`
- `app.kafka.publisher.circuit-breaker.open-seconds`

### Regional failover and idempotency
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

## Metrics touchpoints
- Outbox: `outbox.pending.count`, `outbox.failure.count`, `outbox.batch.size`, `outbox.publish.latency`, `outbox.publish.rate`, `outbox.lag`, `outbox.retry.count`.
- Kafka: `kafka.consumer.errors`, `kafka.consumer.processed.count`, `kafka.consumer.retry.count`, `kafka.consumer.dlq.count`, `kafka.consumer.lag.ms`, `kafka.schema.validation.errors`, `kafka.event.version.distribution`.
- Regional resilience: `failover.events.count`, `region.health.unhealthy.count`, `region.health.dependency.failure.count`, `region.health.redis.slow.count`, `region.health.redis.latency`.
- Cache and rate limiting: `cache.hit.count`, `cache.miss.count`, `cache.error.count`, `cache.degraded.mode.count`, `rate_limit.allowed.count`, `rate_limit.blocked.count`.
