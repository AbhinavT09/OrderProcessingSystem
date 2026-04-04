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

## Individual tool sections with real-time examples

### Tool: Spring Boot

**Why used**
- Provides auto-configuration, dependency injection, actuator, and production-ready web bootstrapping.

**Real-time example**
- At startup, `OrderProcessingApplication` boots `OrderController`, `OrderService`, and `SecurityConfig` automatically.
- A `POST /orders` request reaches a fully wired stack (controller -> service -> repository) without manual object graph creation.

### Tool: Spring Web (MVC)

**Why used**
- Implements HTTP API endpoints with declarative routing and request/response binding.

**Real-time example**
- `OrderController#create` accepts JSON body and `X-Idempotency-Key` header, validates inputs, and returns `201` with structured order payload.

### Tool: Spring Data JPA + Hibernate

**Why used**
- Handles relational persistence and optimistic locking with minimal boilerplate.

**Real-time example**
- Two clients patch the same order status with different versions.
- The stale version triggers an optimistic lock conflict and the API returns `409 CONFLICT`.

### Tool: H2 Database

**Why used**
- Lightweight runtime DB for local development and test environments.

**Real-time example**
- Running `mvn clean test` spins up H2 in-memory using `jdbc:h2:mem:ordersdb`, allowing integration tests to run without external DB setup.

### Tool: Kafka

**Why used**
- Decouples synchronous order creation from delayed status progression.
- Supports reliable retries and scalable asynchronous consumers.

**Real-time example**
- On order creation, `OrderService` publishes `OrderCreatedEvent`.
- `OrderCreatedConsumer` receives the event and only promotes `PENDING -> PROCESSING` after delay window is satisfied.

### Tool: Spring Kafka + Retryable Topics

**Why used**
- Adds retry and dead-letter behavior around consumer logic.

**Real-time example**
- If DB is temporarily unavailable, consumer throws `RetryableProcessingException`.
- Message is retried with exponential backoff; persistent failure routes to DLT handler.

### Tool: Spring Retry

**Why used**
- Enables retry semantics for transient failures in selected flows.

**Real-time example**
- Publisher retry loop plus delayed consumer retries absorb short-lived infrastructure glitches without immediate request/data loss.

### Tool: Spring Security + OAuth2 Resource Server

**Why used**
- Validates JWTs and enforces route-level RBAC for production API hardening.

**Real-time example**
- A token with `ROLE_USER` can create and read orders.
- The same token receives `403 FORBIDDEN` when trying `PATCH /orders/{id}/status` (admin-only route).

### Tool: JWT (HMAC via NimbusJwtDecoder)

**Why used**
- Stateless auth model suitable for horizontally scaled APIs.

**Real-time example**
- Incoming bearer token is validated against `app.security.jwt-secret`.
- `RoleClaimJwtAuthenticationConverter` maps JWT `roles` claim to authorities used by route authorization checks.

### Tool: Micrometer + Prometheus

**Why used**
- Captures runtime performance/reliability metrics and exports for dashboards/alerts.

**Real-time example**
- A spike in failed Kafka processing increments `kafka.consumer.errors`.
- Prometheus scrape detects trend; alert can trigger before customer-visible degradation.

### Tool: OpenTelemetry (via Micrometer tracing bridge)

**Why used**
- Provides end-to-end request trace context across filters/services.

**Real-time example**
- A slow request trace shows time spent in controller, service, and DB operation, correlated by `trace_id` in logs.

### Tool: Logback + MDC Structured JSON logs

**Why used**
- Produces machine-parsable logs with request/order correlation fields.

**Real-time example**
- Production incident investigation filters logs by `request_id` and `order_id` to reconstruct one order's full lifecycle quickly.

### Tool: Bean Validation (Jakarta Validation)

**Why used**
- Enforces API contract constraints at boundary before business processing.

**Real-time example**
- A create payload with empty items and oversized idempotency key fails fast with `400 VALIDATION_FAILED`, avoiding partial processing.

### Tool: Maven Surefire + JUnit 5 + Spring Test

**Why used**
- Runs layered tests (unit/integration/contract) in a repeatable CI-friendly lifecycle.

**Real-time example**
- `mvn clean test` executes:
  - domain state-machine tests,
  - API integration tests with security,
  - Kafka event contract tests.
