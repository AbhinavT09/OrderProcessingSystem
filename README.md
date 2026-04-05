# Order Processing System

Production-ready Spring Boot service for reliable e-commerce order processing with strong write correctness, asynchronous event delivery, and operational resilience.

## Project Highlights

- Hexagonal architecture with clear **Interface / Application / Domain / Infrastructure** boundaries
- CQRS split:
  - write orchestration in `OrderService`
  - read orchestration in `OrderQueryService`
- Domain state pattern for lifecycle transitions (`OrderState` hierarchy)
- Transactional outbox pattern for durable, async Kafka publication
- Global idempotency lifecycle (`IN_PROGRESS`, `COMPLETED`) for safe create retries
- Regional active/passive write gating with dependency health checks
- Redis-backed cache and distributed rate limiting with degradation safeguards
- JWT auth + RBAC + standardized API error contracts
- Micrometer metrics, request correlation, and tracing integration

## Core Features

### Order lifecycle and APIs

- Create orders with optional `X-Idempotency-Key`
- Fetch by id and list by status
- Update status with optimistic concurrency version
- Cancel orders when domain rules allow it
- Lifecycle states: `PENDING`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`

### Write-path correctness

- Idempotent create semantics:
  - `COMPLETED` key returns previously created order
  - `IN_PROGRESS` key prevents unsafe duplicate execution
  - completion is recorded after transaction commit
- Optimistic locking (`@Version`) for concurrent update/cancel safety
- Regional write gating (`RegionalFailoverManager`) blocks writes when node is passive

### Event-driven processing

- `OrderCreated` events are persisted to outbox and published asynchronously
- Outbox pipeline:
  - `OutboxPublisher` (scheduler/backpressure)
  - `OutboxFetcher` (partition claim)
  - `OutboxProcessor` (async publish flow)
  - `OutboxRetryHandler` (retry/backoff policy)
- Kafka consumer (`OrderCreatedConsumer`) supports:
  - delayed processing window
  - processed-event dedupe
  - retry-topic behavior
  - DLT handling with diagnostic logging

### Cache, rate limiting, and resilience

- Redis cache-aside with TTLs, jitter, and fail-soft behavior
- Redis token-bucket rate limiting via Lua script (distributed and atomic)
- Redis/Kafka/DB dependency health monitoring for multi-region failover mode
- Circuit-breaker style protections in Redis cache and Kafka producer paths

### Security and observability

- OAuth2/JWT resource server with role-based endpoint authorization
- Global exception mapping to consistent `ApiError` responses
- Request/region context propagation (`X-Request-Id`, region tags)
- Prometheus metrics and OpenTelemetry tracing support

## Architecture Overview

```text
Client -> Interface HTTP -> Application Services -> Domain Aggregate/State
                                 |                 |
                                 v                 v
                          Ports (Repository/Cache/Event)
                                 |
                                 v
                    Infrastructure (JPA, Redis, Kafka, Resilience)
```

## GitHub Pages Documentation

- Live docs URL: [https://abhinavt09.github.io/OrderProcessingSystem/](https://abhinavt09.github.io/OrderProcessingSystem/)

## Technology Stack

- **Language/Runtime**: Java 17
- **Framework**: Spring Boot 3
- **Web/API**: Spring Web, Jakarta Validation
- **Persistence**: Spring Data JPA (Hibernate), relational DB
- **Messaging**: Kafka, Spring Kafka
- **Caching/Rate Limiting**: Redis (Lettuce)
- **Security**: Spring Security OAuth2 Resource Server (JWT)
- **Observability**: Micrometer, Prometheus endpoint, OpenTelemetry tracing
- **Build/Test**: Maven, JUnit 5, Mockito, Spring test stack

## Repository Structure

```text
src/main/java/com/example/orderprocessing
  interfaces/http          # controllers, DTOs, API errors, exception mapping
  application              # command/query services, ports, events, exceptions
  domain/order             # aggregate, statuses, state implementations
  infrastructure           # persistence, messaging, cache, web, security, resilience
  config                   # runtime/security/redis configuration
docs                       # detailed architecture and operational documentation
```

## API Endpoints

- `POST /orders`
- `GET /orders/{id}`
- `GET /orders?status=<STATUS>`
- `PATCH /orders/{id}/status`
- `PATCH /orders/{id}/cancel`

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.9+
- Kafka broker
- Redis instance (or sentinel/cluster as configured)

### Build and run

```bash
mvn clean compile
mvn test
mvn spring-boot:run
```

### Health and metrics

- Health: `/actuator/health`
- Prometheus: `/actuator/prometheus`

## Configuration Notes

Key runtime areas (see full reference in docs):

- outbox polling, partitioning, retry, and retention
- Kafka topic/consumer group/retry delay/circuit-breaker settings
- cache TTL and Redis resilience controls
- multi-region failover thresholds and idempotency TTL settings

## Testing and Quality

Current coverage includes domain transitions, idempotency lifecycle scenarios, outbox messaging components, cache/rate-limit behavior, failover controls, and API integration paths.
