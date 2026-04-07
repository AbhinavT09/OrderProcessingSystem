---
title: Components and Tooling
nav_order: 3
---

# Components and Tooling

## 1. Component Boundaries

### Interface components

- `OrderController`: HTTP endpoint adapter for command/query operations
- DTOs: request/response transport contracts and validation shape
- `GlobalExceptionHandler`: maps internal exceptions to stable API error schema

### Application components

- `OrderService`: command-side orchestration and transactional write behavior
- `OrderQueryService`: read-side cache-aside orchestration
- `OutboxService`: persistence of integration events for async publication
- ports: repository/cache/publisher abstractions for infrastructure substitution

### Domain components

- `Order`: aggregate root with lifecycle behavior
- `OrderState` hierarchy: transition policy per lifecycle state
- `OrderStatus` and `OrderItem`: domain value representations

### Infrastructure components

- persistence adapters: JPA-backed implementations of application ports
- messaging pipeline: outbox scheduler/fetcher/processor/retry + producer/consumer
- resilience/crosscutting: failover manager, multi-region health indicator (`OUT_OF_SERVICE` when passive), global idempotency coordinator
- web/security: request context, rate limiting, JWT claim conversion

## 2. Why These Components Exist

- Keep transport, business rules, and technical concerns independently changeable
- Maximize write correctness without sacrificing async throughput
- Preserve read availability under cache degradation
- Make failure handling explicit and observable

## 3. Component Interaction Patterns

### 3.1 Command side pattern

- Controller -> `OrderService` -> repository/outbox ports
- idempotency check + regional gating before durable write
- cache eviction after successful mutation

### 3.2 Event publication pattern

- write transaction persists outbox row
- scheduled workers publish asynchronously to Kafka
- retry policy managed by outbox components, not controllers/services

### 3.3 Event consumption pattern

- consumer validates payload
- **dedupe check + processed marker** in one transaction (no aggregate lifecycle transition; `PENDING`→`PROCESSING` is the **scheduler**)
- retries and DLT separation for transient vs terminal failures

## 4. Tooling and Rationale

### Runtime and framework

- **Spring Boot**: dependency management, lifecycle, actuator integration
- **Spring Web**: HTTP routing and validation integration
- **Spring Security/OAuth2 Resource Server**: JWT-based authN/authZ

### Persistence and messaging

- **Spring Data JPA + Hibernate**: relational persistence, optimistic locking, query abstraction
- **Spring Kafka**: producer/consumer plumbing, retry-topic and DLT support
- **Spring Data Redis (Lettuce)**: distributed cache/rate-limiter state and resilience controls

### Observability and operations

- **Micrometer + Prometheus**: application and infra metrics
- **OpenTelemetry bridge**: trace emission for distributed diagnostics

### Build and test

- **Maven**: build lifecycle and dependency management
- **JUnit 5 + Mockito + Spring test stack**: layered unit/integration testing

## 5. Component-Level Failure Behavior

| Component | Failure scenario | Expected behavior |
|---|---|---|
| `RedisCacheProvider` | Redis unavailable | fail-soft cache miss, DB fallback |
| `RateLimitingFilter` | Redis eval failure | fail-open request path |
| `KafkaEventPublisher` | broker send errors | async failure surfaced to outbox retry flow |
| `OrderCreatedConsumer` | duplicate delivery | dedupe marker prevents repeated processing; scheduled job owns `PENDING`→`PROCESSING` |
| `RegionalFailoverManager` | sustained unhealthy deps | switch passive, block writes |
| `OrderService` | stale version update | conflict response, no lost update |

