# Order Processing System Documentation

This documentation describes the current implementation after architecture/package refactors, idempotency lifecycle hardening, and test expansion.

## Quick Start

- Build: `mvn clean compile`
- Run: `mvn spring-boot:run`
- Tests: `mvn test`
- App package root: `src/main/java/com/example/orderprocessing`

## Implementation Snapshot

- **Architecture:** Hexagonal + CQRS + DDD aggregate/state pattern
- **Write correctness:** idempotency (`IN_PROGRESS`/`COMPLETED`), optimistic locking, outbox
- **Async reliability:** partitioned outbox workers, retry/backoff, Kafka consumer retries + DLT
- **Read performance:** cache-aside query service + stampede coalescing + Redis circuit-breaker
- **Resilience:** active/passive regional write gating + dependency health checks
- **Security:** JWT resource server + role-based route authorization + normalized API errors
- **Observability:** Micrometer metrics, structured logs, tracing hooks

## Architecture Diagrams

### End-to-end Runtime View

```mermaid
flowchart LR
    Client[API Client] --> SEC[Security Chain]
    SEC --> API[interfaces/http/controller/OrderController]
    API --> WS[application/service/OrderService]
    API --> QS[application/service/OrderQueryService]

    WS --> ORD[(OrderRepository Port)]
    ORD --> JPA[infrastructure/persistence/adapter/PostgresOrderRepository]
    JPA --> DB[(orders/order_items)]

    WS --> OUT[(OutboxRepository Port)]
    OUT --> OUTJPA[infrastructure/persistence/adapter/JpaOutboxRepository]
    OUTJPA --> ODB[(outbox_events)]

    subgraph Outbox Publish Pipeline
      PUB[infrastructure/messaging/OutboxPublisher] --> FCH[infrastructure/messaging/OutboxFetcher]
      FCH --> ODB
      PUB --> PROC[infrastructure/messaging/OutboxProcessor]
      PROC --> RET[infrastructure/messaging/OutboxRetryHandler]
      PROC --> KEP[infrastructure/messaging/producer/KafkaEventPublisher]
      KEP --> SR[infrastructure/messaging/schema/VersionedJsonOrderCreatedEventSchemaRegistry]
      KEP --> K[(Kafka order.events)]
    end

    K --> KC[infrastructure/messaging/consumer/OrderCreatedConsumer]
    KC --> PR[(ProcessedEventRepository Port)]
    PR --> PRJPA[infrastructure/persistence/adapter/JpaProcessedEventRepository]
    PRJPA --> PDB[(processed_events)]
    KC --> DB

    QS --> CP[(CacheProvider Port)]
    CP --> RCP[infrastructure/cache/RedisCacheProvider]
    RCP --> REDIS[(Redis)]

    SEC --> RL[infrastructure/web/RateLimitingFilter]
    SEC --> RC[infrastructure/web/RequestContextFilter]
    SEC --> JWT[config/security/SecurityConfig]

    WS --> GID[infrastructure/crosscutting/GlobalIdempotencyService]
    WS --> RFM[infrastructure/resilience/RegionalFailoverManager]
```

### Order State Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING
    PENDING --> SHIPPED
    PENDING --> DELIVERED
    PENDING --> CANCELLED
    PROCESSING --> SHIPPED
    PROCESSING --> DELIVERED
    SHIPPED --> DELIVERED
    DELIVERED --> [*]
    CANCELLED --> [*]
```

### Idempotent Create Lifecycle

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OS as OrderService
    participant G as GlobalIdempotencyService
    participant DB as Orders DB
    participant O as Outbox DB

    C->>OS: POST /orders + X-Idempotency-Key
    OS->>G: resolveState(key)
    alt COMPLETED
      OS-->>C: return existing order
    else IN_PROGRESS
      OS-->>C: conflict/retryable response
    else ABSENT
      OS->>G: markInProgress(key)
      OS->>DB: save order
      OS->>O: save outbox row
      OS-->>C: 201 Created
      OS->>G: afterCommit markCompleted(key, orderId)
    end
```

## Documentation Structure

### Core Guides

- [Design and Architecture](./design-and-architecture.md)
- [Components and Tooling](./components-and-tooling.md)
- [Observability and Operations](./observability-and-operations.md)
- [Testing and Quality](./testing-and-quality.md)
- [Use Cases and Failure Scenarios](./use-cases-and-failure-scenarios.md)

### Layered Reference

- [Reference Index](./reference/index.md)
- [Interface HTTP Layer](./reference/api-layer.md)
- [Application Layer](./reference/application-layer.md)
- [Domain Layer](./reference/domain-layer.md)
- [Infrastructure Layer](./reference/infrastructure-layer.md)
- [Configuration and Runtime](./reference/configuration-and-runtime.md)
- [Folder and Class Reference](./folder-and-class-reference.md)
