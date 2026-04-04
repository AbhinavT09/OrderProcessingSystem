# Order Processing System - Project Documentation

This documentation reflects the **current implementation** of the project, including outbox publishing, Redis-backed caching/rate-limiting, Kafka reliability controls, and observability.

## System capabilities

- Hexagonal architecture with CQRS (`OrderService` write side, `OrderQueryService` read side)
- Domain aggregate with State Pattern (`Order` + `OrderState` hierarchy)
- Transactional Outbox pattern for reliable event publishing
- Kafka delayed processing with retry topics + DLQ
- Redis-backed distributed cache (JSON serialization + TTLs)
- Redis-backed distributed token-bucket rate limiting (Lua + atomic updates)
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
      KEP --> K[(Kafka order.events)]
    end

    K --> KC[OrderCreatedConsumer]
    KC --> PR[(ProcessedEventRepository Port)]
    PR --> PRJPA[JpaProcessedEventRepository]
    PRJPA --> PDB[(processed_events)]
    KC --> DB

    QS --> CP[(CacheProvider Port)]
    CP --> RCP[RedisCacheProvider]
    RCP --> REDIS[(Redis)]

    SEC --> RL[RateLimitingFilter Redis Token Bucket]
    SEC --> RC[RequestContextFilter]
    SEC --> JWT[JWT auth + RBAC]
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

## Documentation map

- [Design and Architecture](./design-and-architecture.md)
- [Components and Tooling Rationale](./components-and-tooling.md)
- [Observability, Logging, Monitoring](./observability-and-operations.md)
- [Folder and Class Reference](./folder-and-class-reference.md)
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
