# Folder and Class Reference (Implementation-Accurate)

This is a class-by-class implementation reference for the current codebase.

## Root package

### `OrderProcessingApplication`
- Bootstraps Spring Boot
- Enables retry and scheduling (for outbox publisher)

## API layer

### `api/controller/OrderController`
- REST entrypoint for order operations
- Delegates writes to `OrderService`, reads to `OrderQueryService`

### DTOs
- `CreateOrderRequest`
- `OrderItemRequest`
- `UpdateOrderStatusRequest`
- `OrderResponse`

### Error handling
- `ApiError`: standardized response envelope
- `GlobalExceptionHandler`: maps typed exceptions to HTTP codes

## Application layer

### Services

#### `OrderService`
- Write-side orchestration
- Idempotent create behavior
- Optimistic locking conflict checks
- Writes outbox records (no direct Kafka publish)
- Cache invalidation on writes
- Enforces regional write policy via failover manager
- Uses global idempotency mapping for cross-region duplicate protection

#### `OrderQueryService`
- Read-side orchestration
- Cache-aside by ID and status list
- Stampede protection with key-lock coalescing
- TTL writes via `CacheProvider.put(key, value, ttl)`

#### `OrderMapper`
- Maps API DTOs <-> domain <-> entities

#### `OutboxService`
- Serializes event payload via schema registry and persists `OutboxEntity`

### Ports

- `OrderRepository`
- `EventPublisher`
- `CacheProvider`
- `ProcessedEventRepository`
- `OutboxRepository`

### Events and exceptions

- `OrderCreatedEvent`
- `NotFoundException`
- `ConflictException`
- `InfrastructureException`

## Domain layer

### `Order`
- Aggregate root with behavior methods
- Delegates transition rules to state objects

### `OrderItem`
- Value object for line items

### `OrderStatus`
- Lifecycle statuses enum

### State package

- `OrderState` (interface)
- `AbstractOrderState` (common transition logic)
- `OrderStateFactory`
- `PendingState`
- `ProcessingState`
- `ShippedState`
- `DeliveredState`
- `CancelledState`

## Infrastructure layer

### Cache

#### `RedisCacheProvider`
- Redis JSON cache implementation
- Metrics: hit/miss/error
- Fail-safe fallback behavior
- Redis circuit breaker + TTL jitter + command latency metrics

### Messaging

#### `KafkaEventPublisher`
- Outbound Kafka adapter
- Retries + circuit breaker
- Delegates schema validation/serialization to central schema registry

#### `OutboxPublisher`
- Scheduled outbox dispatcher
- Exponential backoff + max retry
- Metrics: pending/failure/publish latency/batch size/publish rate/lag/retry
- Partition-aware parallel workers and archive cleanup

#### `OrderCreatedConsumer`
- Kafka consumer with retry-topic strategy
- Manual offset acknowledgment
- Idempotent consume via processed-events table
- DLQ enriched logging + metrics
- Versioned schema parse/validation fallback

#### `KafkaConsumerRebalanceObserver`
- Emits logs for consumer lifecycle and partition pause/resume

#### `DelayedProcessingNotReadyException`
- Signals delayed retry path

#### `RetryableProcessingException`
- Signals transient processing retry path

### Messaging schema

#### `schema/OrderCreatedEventSchemaRegistry`
- Abstraction for event schema handling.

#### `schema/VersionedJsonOrderCreatedEventSchemaRegistry`
- Current implementation for versioned JSON schema contracts.
- Handles backward/forward-compatible parse and validation.

#### `schema/EventSchemaValidationException`
- Raised for invalid event contracts.

### Persistence

#### Adapters
- `PostgresOrderRepository`
- `JpaProcessedEventRepository`
- `JpaOutboxRepository`

#### Repositories
- `SpringOrderJpaRepository`
- `SpringProcessedEventJpaRepository`
- `SpringOutboxJpaRepository`

#### Entities
- `OrderEntity`
- `OrderItemEmbeddable`
- `ProcessedEventEntity`
- `OutboxEntity`
- `OutboxStatus`
- `OutboxArchiveEntity`

#### Additional repositories
- `SpringOutboxArchiveJpaRepository`

### Security

#### `RoleClaimJwtAuthenticationConverter`
- JWT `roles` claim -> Spring authorities

### Web filters

#### `RequestContextFilter`
- Request ID + region ID propagation and region-aware HTTP metrics

#### `RateLimitingFilter`
- Redis Lua token-bucket limiter
- Key: `userId:path:ip`
- Metrics: allowed/blocked
- Returns 429 `ApiError`

## Config

### `SecurityConfig`
- Stateless security model
- Route-level RBAC
- JWT decoder setup
- 401/403 JSON handlers
- Rate limiting filter wiring in chain

### `application.yml`
- Datasource/JPA
- Redis host/port/timeout + cluster/sentinel/pool/HA settings
- Kafka listener/publisher/retry settings
- Outbox scheduler/retry settings
- Cache TTL settings
- Security + rate-limit settings
- Actuator/tracing/metrics/logging settings
- Multi-region failover/RTO/RPO/global-idempotency settings

### Resilience

#### `infrastructure/resilience/RegionalFailoverManager`
- Periodic DB/Redis/Kafka health checks.
- Active/passive node-state transitions for DR protection.

#### `infrastructure/resilience/MultiRegionHealthIndicator`
- Exposes multi-region state on actuator health endpoints.

#### `infrastructure/idempotency/GlobalIdempotencyService`
- Redis-based global key lock + completion mapping.

