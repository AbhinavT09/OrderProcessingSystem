# Order Processing System - Project Documentation

This documentation reflects the **current implementation** of the project, including outbox publishing, Redis-backed caching/rate-limiting, Kafka schema evolution controls, and multi-region resilience scaffolding.

## System capabilities

- Hexagonal architecture with CQRS (`OrderService` write side, `OrderQueryService` read side)
- Domain aggregate with State Pattern (`Order` + `OrderState` hierarchy)
- Transactional Outbox pattern for reliable event publishing
- Kafka delayed processing with retry topics + DLQ
- Redis-backed distributed cache (JSON serialization + TTLs)
- Redis-backed distributed token-bucket rate limiting (Lua + atomic updates)
- Redis HA-ready configuration (cluster/sentinel, reconnect, pooling, timeout)
- Kafka versioned event schema validation (`schemaVersion`) with compatibility handling
- Multi-region failover manager with health-driven write gating
- Global idempotency guard for cross-region duplicate prevention
- JWT auth + RBAC + validation + structured API errors
- Prometheus metrics + tracing + structured JSON logs

## Interactive architecture diagrams

### Full runtime flow

```mermaid
flowchart LR
    Client[API Client] --> SEC[Security Chain]
    SEC --> API[OrderController]

    API --> WS[OrderService write]
    API --> QS[OrderQueryService read]

    WS --> ORD[(OrderRepository Port)]
    ORD --> JPA[PostgresOrderRepository]
    JPA --> DB[(orders/order_items)]

    WS --> OUT[(OutboxRepository Port)]
    OUT --> OUTJPA[JpaOutboxRepository]
    OUTJPA --> ODB[(outbox_events)]

    subgraph Async Outbox Publish
      PUB[OutboxPublisher scheduler] --> ODB
      PUB --> KEP[KafkaEventPublisher]
      KEP --> SR[VersionedJsonSchemaRegistry]
      KEP --> K[(Kafka order.events)]
    end

    K --> KC[OrderCreatedConsumer]
    KC --> SR
    KC --> PR[(ProcessedEventRepository Port)]
    PR --> PRJPA[JpaProcessedEventRepository]
    PRJPA --> PDB[(processed_events)]
    KC --> DB

    QS --> CP[(CacheProvider Port)]
    CP --> RCP[RedisCacheProvider]
    RCP --> REDIS[(Redis)]
    RCP --> CB[Redis Circuit Breaker]

    SEC --> RL[RateLimitingFilter Redis Token Bucket]
    SEC --> RC[RequestContextFilter]
    SEC --> JWT[JWT auth + RBAC]

    RC --> RMDC[region_id in logs and metrics]
    WS --> GID[GlobalIdempotencyService]
    WS --> RFM[RegionalFailoverManager]
    RFM --> HC[MultiRegionHealthIndicator]
```

### State transition model

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING: delayed event OR update status
    PENDING --> SHIPPED: update status
    PENDING --> DELIVERED: update status
    PENDING --> CANCELLED: cancel

    PROCESSING --> SHIPPED
    PROCESSING --> DELIVERED
    SHIPPED --> DELIVERED

    DELIVERED --> [*]
    CANCELLED --> [*]
```

### Outbox reliability sequence

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OS as OrderService
    participant DB as Orders DB
    participant ODB as Outbox DB
    participant OP as OutboxPublisher
    participant K as Kafka

    C->>OS: create order
    OS->>DB: save OrderEntity
    OS->>ODB: save OutboxEntity(PENDING)
    OS-->>C: 201 Created

    loop scheduler poll
      OP->>ODB: fetch PENDING/FAILED
      OP->>K: publish event
      alt publish success
        OP->>ODB: mark SENT
      else publish fails
        OP->>ODB: retry_count++, mark FAILED
      end
    end
```

### Multi-region failover control flow

```mermaid
sequenceDiagram
    autonumber
    participant HM as RegionalFailoverManager
    participant DB as Database
    participant R as Redis
    participant K as Kafka
    participant OS as OrderService

    loop every poll interval
      HM->>DB: health check
      HM->>R: health check
      HM->>K: health check
      alt dependency unhealthy threshold reached
        HM->>HM: switch node state ACTIVE -> PASSIVE
      else healthy
        HM->>HM: keep/restore ACTIVE
      end
    end

    OS->>HM: allowsWrites()
    alt PASSIVE
      OS-->>OS: throw InfrastructureException (503)
    else ACTIVE
      OS-->>OS: continue write
    end
```

### Kafka schema evolution flow

```mermaid
flowchart TD
    E[OrderCreatedEvent] --> V{schemaVersion present?}
    V -- no --> D[Default to v1 parse rules]
    V -- yes --> C[Validate supported/forward-compatible version]
    D --> S[Validate required fields]
    C --> S
    S --> P[Producer/Outbox serialize + publish]
    S --> Q[Consumer deserialize + process]
    S --> M[kafka.event.version.distribution]
    S -->|invalid payload| ERR[kafka.schema.validation.errors++]
```

## Documentation map

- [Design and Architecture](./design-and-architecture.md)
- [Components and Tooling Rationale](./components-and-tooling.md)
- [Observability, Logging, Monitoring](./observability-and-operations.md)
- [Use Cases and Failure Cases](./use-cases-and-failure-scenarios.md)
- [Folder and Class Reference (Overview)](./folder-and-class-reference.md)
- [Reference Documentation (Nested)](./reference/index.md)
- [Testing and Quality Strategy](./testing-and-quality.md)

## Runtime prerequisites

- Java 17
- Maven 3.9+
- Redis (cache + distributed rate limiting)
- Kafka broker
- Optional OTEL collector (`http://localhost:4318/v1/traces`)

## Run

```bash
mvn clean compile
mvn spring-boot:run
```
