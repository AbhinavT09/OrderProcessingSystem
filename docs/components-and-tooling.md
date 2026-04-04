# Components and Tooling Rationale

## API layer components

- `OrderController`
  - Exposes REST endpoints under `/orders`.
  - Delegates write and read paths separately.
  - Applies request validation and header constraints.
- DTOs (`CreateOrderRequest`, `OrderItemRequest`, `UpdateOrderStatusRequest`, `OrderResponse`)
  - Keep API contracts explicit and stable.
  - Encapsulate validation annotations.
- `GlobalExceptionHandler` + `ApiError`
  - Converts exceptions to consistent JSON errors.

## Application layer components

- `OrderService` (write path)
  - Handles create/update/cancel use-cases.
  - Implements idempotency and optimistic lock conflict handling.
  - Publishes `OrderCreatedEvent`.
  - Invalidates relevant cache keys.
- `OrderQueryService` (read path)
  - Cache-aside reads by id and status.
  - Coalesces concurrent misses to reduce stampede.
- `OrderMapper`
  - Maps between API DTOs, domain aggregate, and persistence entities.
- Ports (`OrderRepository`, `EventPublisher`, `CacheProvider`, `ProcessedEventRepository`)
  - Define dependency boundaries for infrastructure integrations.

## Domain layer components

- `Order` aggregate root
  - Central business object with lifecycle behavior.
- `OrderState` hierarchy
  - Implements transition policy without `if-else` chains.
- `OrderItem` value object
- `OrderStatus` enum

## Infrastructure layer components

### Persistence

- `PostgresOrderRepository` adapter over `SpringOrderJpaRepository`
- `JpaProcessedEventRepository` adapter over `SpringProcessedEventJpaRepository`
- Entities:
  - `OrderEntity` (with `@Version`, idempotency key uniqueness)
  - `OrderItemEmbeddable`
  - `ProcessedEventEntity` (event dedupe)

### Messaging

- `KafkaEventPublisher`
  - Serializes events and publishes keyed by `orderId`.
  - Retry with exponential backoff.
  - Local circuit breaker to avoid thrashing during outages.
- `OrderCreatedConsumer`
  - Delayed processing using retry topics.
  - Promotes pending orders to processing after delay.
  - Persists processed marker for exactly-once effect at application layer.

### Cache

- `RedisCacheProvider` (in-memory implementation currently)
  - Implements `CacheProvider` port.
  - Fail-safe operations (cache failure does not break core flow).

### Web and security

- `RequestContextFilter`
  - Request ID generation/propagation.
  - HTTP request metrics emission.
- `RateLimitingFilter`
  - In-memory fixed-window throttling.
  - Returns `429` JSON payload on block.
- `SecurityConfig`
  - Stateless JWT auth and RBAC route policy.
- `RoleClaimJwtAuthenticationConverter`
  - Maps JWT `roles` claim to Spring authorities.

## Tooling: what and why

### Spring Boot

Used as the application framework for rapid, convention-driven wiring of web, security, data, and metrics.

### Spring Data JPA

Used for repository abstraction and entity mapping:

- Reduces persistence boilerplate.
- Supports optimistic locking natively.

### Kafka

Used for asynchronous delayed processing and decoupling:

- Separates order creation from delayed state promotion.
- Supports retries and dead-letter handling.
- Key-based partitioning by `orderId` keeps order-level event sequence stable.

### Spring Kafka + Retryable topics

Used for resilient consumers:

- Exponential backoff retries for transient failures.
- DLT routing for poison/non-recoverable cases.

### Micrometer + Prometheus

Used for metrics instrumentation:

- Request counts/latency/error rates.
- Business operation timings.
- Kafka lag summaries.

### OpenTelemetry tracing

Used to emit trace context and span data to OTLP endpoint.

### Spring Security + OAuth2 Resource Server

Used for JWT validation and RBAC enforcement:

- Stateless model fits API services.
- Route-level policies are explicit and auditable.

### H2 (runtime default)

Used for local development simplicity (no external DB dependency).

### Maven

Used for build lifecycle, dependency management, and reproducible test runs.
