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

#### `OrderQueryService`
- Read-side orchestration
- Cache-aside by ID and status list
- Stampede protection with key-lock coalescing
- TTL writes via `CacheProvider.put(key, value, ttl)`

#### `OrderMapper`
- Maps API DTOs <-> domain <-> entities

#### `OutboxService`
- Serializes event payload and persists `OutboxEntity`

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

### Messaging

#### `KafkaEventPublisher`
- Outbound Kafka adapter
- Retries + circuit breaker

#### `OutboxPublisher`
- Scheduled outbox dispatcher
- Exponential backoff + max retry
- Metrics: pending/failure/publish latency

#### `OrderCreatedConsumer`
- Kafka consumer with retry-topic strategy
- Manual offset acknowledgment
- Idempotent consume via processed-events table
- DLQ enriched logging + metrics

#### `KafkaConsumerRebalanceObserver`
- Emits logs for consumer lifecycle and partition pause/resume

#### `DelayedProcessingNotReadyException`
- Signals delayed retry path

#### `RetryableProcessingException`
- Signals transient processing retry path

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

### Security

#### `RoleClaimJwtAuthenticationConverter`
- JWT `roles` claim -> Spring authorities

### Web filters

#### `RequestContextFilter`
- Request ID propagation and HTTP metrics

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
- Redis host/port/timeout
- Kafka listener/publisher/retry settings
- Outbox scheduler/retry settings
- Cache TTL settings
- Security + rate-limit settings
- Actuator/tracing/metrics/logging settings

