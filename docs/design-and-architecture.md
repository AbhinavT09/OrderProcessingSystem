# Design and Architecture

## Architectural style

The project follows a **Hexagonal (Ports and Adapters)** architecture with **CQRS**.

- Domain contains business rules and lifecycle invariants.
- Application layer orchestrates use-cases and depends on ports.
- Infrastructure layer provides adapters to Kafka, persistence, cache, and web filters.
- API layer exposes HTTP endpoints and maps transport concerns.

CQRS split:

- **Write side:** `OrderService`
- **Read side:** `OrderQueryService`

## Why this architecture

- Keeps domain logic isolated from framework details.
- Makes external dependencies replaceable via ports.
- Supports independent evolution of reads/writes.
- Improves testability: domain can be tested without Spring/Kafka/DB.

## Layered package map

```mermaid
flowchart TD
    D[domain] --> A[application]
    A --> I[infrastructure]
    A --> API[api]

    subgraph domain
      DM[model\nOrder, OrderItem, OrderStatus]
      DS[state\nOrderState + concrete states]
    end

    subgraph application
      AP[port interfaces]
      AS[services\nOrderService / OrderQueryService]
      AE[event + exceptions + mapper]
    end

    subgraph infrastructure
      IP[persistence adapters + entities + repos]
      IM[messaging adapters + consumer]
      IC[cache adapter]
      IW[web filters]
      IS[security auth converter]
    end

    subgraph api
      AC[OrderController]
      AD[DTOs]
      AX[GlobalExceptionHandler + ApiError]
    end
```

## Domain model design

### Aggregate root: `Order`

`Order` encapsulates state and lifecycle transitions. It exposes behavior:

- `updateStatus(target)`
- `cancel()`
- `promotePendingToProcessing()`

The aggregate carries:

- `id`
- `createdAt`
- `idempotencyKey`
- `items`
- `version`
- `currentState`

### State Pattern

Order transition logic is delegated to `OrderState` implementations:

- `PendingState`
- `ProcessingState`
- `ShippedState`
- `DeliveredState`
- `CancelledState`

`AbstractOrderState` centralizes transition lookup and conflict handling. Invalid transitions throw `ConflictException`.

### Lifecycle rules enforced

- Cancel allowed only while `PENDING`.
- Pending can auto-promote to processing after delay.
- Terminal states (`DELIVERED`, `CANCELLED`) do not transition.

## CQRS and consistency model

- Writes happen through `OrderService` and persist to DB.
- Reads happen through `OrderQueryService` with cache-aside strategy.
- Eventual processing (`PENDING -> PROCESSING`) is asynchronous via Kafka event consumption.
- Consumer idempotency ensures retries do not duplicate side effects.

## Concurrency and integrity strategy

- **Optimistic locking:** `@Version` on `OrderEntity.version`.
- **Version check:** API update request includes expected version.
- **Idempotent creates:** `X-Idempotency-Key` mapped to unique DB key.
- **Consumer idempotency:** `processed_events` table keyed by unique `eventId`.

## Error boundary strategy

- Domain/application failures surfaced as typed exceptions:
  - `NotFoundException`
  - `ConflictException`
  - `InfrastructureException`
- API exception handler maps these to stable HTTP responses and structured `ApiError` payloads.

## Security architecture summary

- Stateless JWT Bearer authentication.
- `roles` claim mapped to `ROLE_*` authorities.
- Route-level RBAC in `SecurityConfig`.
- Rate limiter as servlet filter before auth chain.
- Request correlation middleware sets/propagates `X-Request-Id`.

## Additional interactive design docs

### Sequence: create order and delayed processing

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OC as OrderController
    participant OS as OrderService
    participant OR as OrderRepository
    participant EP as EventPublisher/KafkaEventPublisher
    participant K as Kafka
    participant KC as OrderCreatedConsumer
    participant PER as ProcessedEventRepository

    C->>OC: POST /orders (+ X-Idempotency-Key)
    OC->>OS: createOrder(request, key)
    OS->>OR: save(order=PENDING)
    OR-->>OS: saved OrderEntity
    OS->>EP: publishOrderCreated(event)
    EP->>K: send(topic=order.events, key=orderId)
    OS-->>OC: 201 OrderResponse
    OC-->>C: Created

    K-->>KC: consume(event payload)
    KC->>PER: existsByEventId(eventId)?
    alt not processed and delay elapsed
        KC->>OR: findById(orderId)
        KC->>OR: save(order=PROCESSING)
        KC->>PER: save(processed marker)
    else already processed
        KC-->>KC: skip duplicate safely
    else not ready yet
        KC-->>K: throw DelayedProcessingNotReadyException (retry later)
    end
```

### Security request path and policy enforcement

```mermaid
flowchart LR
    REQ[Incoming HTTP request] --> RL[RateLimitingFilter]
    RL --> RC[RequestContextFilter]
    RC --> JWT[JWT Authentication]
    JWT --> RBAC[Route RBAC checks]
    RBAC --> CTRL[OrderController]

    RBAC -->|No token / invalid token| U401[401 UNAUTHORIZED JSON ApiError]
    RBAC -->|Insufficient role| U403[403 FORBIDDEN JSON ApiError]
    RL -->|Over limit| U429[429 RATE_LIMITED JSON ApiError]
```

### Cache-aside and stampede-control behavior

```mermaid
flowchart TD
    RQ[Read request] --> CK{Cache hit?}
    CK -- Yes --> RET[Return cached response]
    CK -- No --> LK[Acquire key lock]
    LK --> RECHK{Cache filled by other thread?}
    RECHK -- Yes --> RET
    RECHK -- No --> DB[Load from repository]
    DB --> PUT[Put in cache]
    PUT --> UNLK[Release lock]
    UNLK --> RET
```
