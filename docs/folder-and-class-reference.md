# Folder and Class Reference (Detailed)

This section documents every source folder and class currently in the project.

## Folder map

```text
src/main/java/com/example/orderprocessing
├── OrderProcessingApplication.java
├── api
│   ├── controller
│   ├── dto
│   ├── error
│   └── exception
├── application
│   ├── event
│   ├── exception
│   ├── port
│   └── service
├── config
├── domain
│   ├── model
│   └── state
└── infrastructure
    ├── cache
    ├── messaging
    ├── persistence
    │   ├── adapter
    │   ├── entity
    │   └── repository
    ├── security
    └── web
```

## Root package

### `OrderProcessingApplication`
- Role: Spring Boot app entry point.
- Key behavior: bootstraps context, enables retry support.

## API package

### `api/controller/OrderController`
- Role: HTTP transport layer for `/orders`.
- Endpoints:
  - `POST /orders`
  - `GET /orders/{id}`
  - `PATCH /orders/{id}/status`
  - `GET /orders`
  - `PATCH /orders/{id}/cancel`
- Notes: delegates to `OrderService` and `OrderQueryService`; validates request/header fields.

### `api/dto/CreateOrderRequest`
- Role: request contract for create order.
- Validation: `items` must be non-empty and element-valid.

### `api/dto/OrderItemRequest`
- Role: API view of item payload.
- Validation: product non-blank, quantity >= 1, price >= 0.

### `api/dto/UpdateOrderStatusRequest`
- Role: request contract for status updates.
- Validation: `status` non-null, `version` non-null and >= 0.

### `api/dto/OrderResponse`
- Role: response contract for order resources.
- Fields: id, status, createdAt, items.

### `api/error/ApiError`
- Role: canonical error payload shape.
- Fields: code, message, requestId, timestamp.

### `api/exception/GlobalExceptionHandler`
- Role: centralized exception-to-HTTP mapper.
- Maps:
  - `NotFoundException -> 404`
  - `ConflictException -> 409`
  - `InfrastructureException -> 503`
  - Validation and malformed input -> 400
  - fallback -> 500

## Application package

### `application/event/OrderCreatedEvent`
- Role: Kafka event contract for order creation.
- Fields: `eventId`, `eventType`, `orderId`, `occurredAt`.

### `application/exception/NotFoundException`
- Role: semantic missing-resource exception.

### `application/exception/ConflictException`
- Role: semantic business/conflict exception.

### `application/exception/InfrastructureException`
- Role: wrapper for external dependency failures.

### `application/port/OrderRepository`
- Role: persistence port for orders.
- Operations: save/find/list/findByStatus/findByIdempotencyKey.

### `application/port/EventPublisher`
- Role: outbound event publishing port.

### `application/port/CacheProvider`
- Role: cache abstraction port.
- Operations: `get`, `put`, `evict`.

### `application/port/ProcessedEventRepository`
- Role: dedupe marker repository port for consumed events.

### `application/service/OrderService`
- Role: write-side application service.
- Responsibilities:
  - create order with idempotency support
  - publish order-created event
  - status update/cancel with optimistic lock checks
  - cache invalidation
  - write-path metrics and logging context

### `application/service/OrderQueryService`
- Role: read-side application service.
- Responsibilities:
  - get by id / list by status
  - cache-aside loading
  - cache stampede prevention with key locks
  - read-path metrics

### `application/service/OrderMapper`
- Role: transformation between API DTOs, domain aggregate, and persistence entities.

### `application/service/CachedOrderList`
- Role: typed wrapper for caching list results.

## Config package

### `config/SecurityConfig`
- Role: security policy and JWT decoder wiring.
- Responsibilities:
  - stateless session policy
  - endpoint authorization rules
  - OAuth2 JWT resource server setup
  - JSON 401/403 response formatting
  - rate-limiter filter insertion in chain

## Domain package

### `domain/model/Order`
- Role: aggregate root with lifecycle behavior.
- Contains state object, immutable identifiers, and behavior methods.

### `domain/model/OrderItem`
- Role: value object for line-item data.

### `domain/model/OrderStatus`
- Role: status enumeration.

### `domain/state/OrderState`
- Role: interface for polymorphic state behavior.

### `domain/state/AbstractOrderState`
- Role: shared transition logic + default conflict behavior.

### `domain/state/OrderStateFactory`
- Role: maps enum status to concrete state objects.

### `domain/state/PendingState`
- Role: pending-specific behavior; supports cancel and delayed promotion.

### `domain/state/ProcessingState`
- Role: processing-specific transition rules.

### `domain/state/ShippedState`
- Role: shipped-specific transition rules.

### `domain/state/DeliveredState`
- Role: terminal delivered state.

### `domain/state/CancelledState`
- Role: terminal cancelled state.

## Infrastructure package

### Cache

#### `infrastructure/cache/RedisCacheProvider`
- Role: cache adapter implementing `CacheProvider`.
- Notes: currently backed by in-memory `ConcurrentHashMap` with fail-safe behavior.

### Messaging

#### `infrastructure/messaging/KafkaEventPublisher`
- Role: `EventPublisher` adapter.
- Responsibilities:
  - serialize and publish `OrderCreatedEvent`
  - key by `orderId` for partition affinity
  - retry + circuit breaker

#### `infrastructure/messaging/OrderCreatedConsumer`
- Role: asynchronous delayed event processor.
- Responsibilities:
  - parse payload
  - idempotency check via processed-event repo
  - enforce processing delay via retryable exceptions
  - promote pending orders to processing
  - write processed marker

#### `infrastructure/messaging/DelayedProcessingNotReadyException`
- Role: signal to retry later when delay threshold not met.

#### `infrastructure/messaging/RetryableProcessingException`
- Role: signal transient processing failure for retry pipeline.

### Persistence adapters

#### `infrastructure/persistence/adapter/PostgresOrderRepository`
- Role: `OrderRepository` adapter using Spring Data JPA repo.

#### `infrastructure/persistence/adapter/JpaProcessedEventRepository`
- Role: `ProcessedEventRepository` adapter using Spring Data JPA repo.

### Persistence entities

#### `infrastructure/persistence/entity/OrderEntity`
- Role: DB model for orders.
- Key details:
  - `@Version` for optimistic locking
  - unique idempotency key
  - eager element-collection for items

#### `infrastructure/persistence/entity/OrderItemEmbeddable`
- Role: embedded line-item persistence shape.

#### `infrastructure/persistence/entity/ProcessedEventEntity`
- Role: consumed event dedupe marker.
- Key detail: unique `eventId` constraint.

### Persistence repositories

#### `infrastructure/persistence/repository/SpringOrderJpaRepository`
- Role: Spring Data repository for `OrderEntity`.
- Additional methods: status-ordered find, idempotency-key find.

#### `infrastructure/persistence/repository/SpringProcessedEventJpaRepository`
- Role: Spring Data repository for `ProcessedEventEntity`.
- Additional method: `existsByEventId`.

### Security

#### `infrastructure/security/RoleClaimJwtAuthenticationConverter`
- Role: maps JWT `roles` claim to `ROLE_*` authorities.

### Web filters

#### `infrastructure/web/RequestContextFilter`
- Role: request correlation and HTTP metric instrumentation.
- Behavior:
  - generate/propagate `X-Request-Id`
  - set MDC fields
  - record latency/count/error metrics

#### `infrastructure/web/RateLimitingFilter`
- Role: fixed-window in-memory rate limiting.
- Behavior:
  - per key: method + URI + subject-hash + IP
  - blocks over limit with HTTP 429 `ApiError`
  - increments blocked counter metric

## Resource/config files

### `src/main/resources/application.yml`
- Central runtime config for:
  - datasource/JPA
  - Kafka producer/consumer/retry/delay
  - security secret and rate limits
  - actuator endpoints
  - metrics percentiles
  - OTEL tracing export
  - JSON logging pattern

## Test classes reference

### `OrderAggregateTest`
- Domain state machine and invariants.

### `OrderControllerIntegrationTest`
- API security + validation + basic endpoint integration.

### `OrderCreatedEventContractTest`
- Kafka event schema contract.

### `OrderCreatedConsumerUnitTest`
- Consumer promotion and dedupe behavior.
