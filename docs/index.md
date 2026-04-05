# Order Processing System Documentation

This documentation describes the current implementation after adaptive retry, dynamic rate limiting, active-active consistency controls, transactional Kafka publishing, and system-wide backpressure propagation.

## Quick Start

- Build: `mvn clean compile`
- Run: `mvn spring-boot:run`
- Tests: `mvn test`
- App package root: `src/main/java/com/example/orderprocessing`

## Implementation Snapshot

- **Architecture:** Hexagonal + CQRS + DDD aggregate/state pattern
- **Write correctness:** idempotency (`IN_PROGRESS`/`COMPLETED`), optimistic locking, outbox
- **Async reliability:** partitioned outbox workers, adaptive retry classification, Kafka consumer retries + DLT
- **Read performance:** cache-aside query service + stampede coalescing + Redis circuit-breaker
- **Resilience:** active/passive failover + active-active consistency conflict checks + system-wide backpressure
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
    WS --> RCM[infrastructure/resilience/RegionalConsistencyManager]
    WS --> BPM[infrastructure/resilience/BackpressureManager]
    RL --> RPP[infrastructure/web/ratelimit/RedisBackedRateLimitPolicyProvider]
    RPP --> REDIS
    RPP --> BPM
    KC --> BPM
    PROC --> RPS[infrastructure/messaging/retry/RetryPolicyStrategy]
    KC --> RPS
    RCM --> RFM[infrastructure/resilience/RegionalFailoverManager]
    RCM --> CRS[infrastructure/resilience/conflict/ConflictResolutionStrategy]
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

### Adaptive Outbox Retry Lifecycle

```mermaid
flowchart TD
    PF[Publish Failure] --> RP[RetryPolicyStrategy.plan]
    RP --> CLS{Classification}
    CLS -->|TRANSIENT| TD1[Short adaptive delay]
    CLS -->|SEMI_TRANSIENT| TD2[Medium adaptive delay]
    CLS -->|PERMANENT| TD3[Terminalize row]
    TD1 --> BP1[Scale delay by BackpressureManager]
    TD2 --> BP2[Scale delay by BackpressureManager]
    BP1 --> JT1[Apply jitter + max cap]
    BP2 --> JT2[Apply jitter + max cap]
    JT1 --> NXT1[Set nextAttemptAt]
    JT2 --> NXT2[Set nextAttemptAt]
    TD3 --> NXT3[Park as terminal FAILED]
    NXT1 --> OBS1[Emit retry.delay.ms]
    NXT2 --> OBS2[Emit retry.delay.ms]
    NXT3 --> OBS3[Emit retry.classification.count]
```

### Dynamic Rate Limiting Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Filter as RateLimitingFilter
    participant Provider as RedisBackedRateLimitPolicyProvider
    participant Redis
    participant BP as BackpressureManager

    Client->>Filter: HTTP request
    Filter->>Provider: resolve(request)
    Provider->>Redis: policy config read (cached)
    Provider->>BP: throttlingFactor()
    Provider-->>Filter: RateLimitPolicy(capacity, burst, window)
    Filter->>Redis: token bucket Lua
    alt token bucket success
        Filter-->>Client: allow or 429
    else script failure and fallback enabled
        Filter->>Redis: sliding window Lua
        Filter-->>Client: allow or 429
    else Redis unavailable
        Filter-->>Client: fail-open allow
    end
```

### Global Backpressure Propagation

```mermaid
flowchart LR
    KC[Kafka Consumer lag recorder] --> BPM[BackpressureManager]
    ODB[(outbox_events backlog)] --> BPM
    DBP[(DB pool utilization)] --> BPM
    BPM --> LVL{Level}
    LVL -->|NORMAL| N1[No throttling]
    LVL -->|ELEVATED| N2[Tighten rate policies]
    LVL -->|CRITICAL| N3[Reject write requests]
    N2 --> RL[RateLimitingFilter]
    N3 --> OS[OrderService write path]
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
