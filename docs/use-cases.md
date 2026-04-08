---
title: Use Cases
nav_order: 6
---

# Use Cases (Controller APIs)

This page documents **successful end-to-end flows** for every HTTP endpoint exposed by `OrderController`, using the same sequence-diagram pattern throughout. Cross-cutting behavior (JWT resource server, filters, and error envelopes) is summarized once; each API then has its own diagram.

---

## Cross-cutting behavior

- **Authentication:** Clients send `Authorization: Bearer <JWT>`. Roles come from the token (`USER` / `ADMIN`) except where noted.
- **Authorization:** See [Security and Authorization]({{ '/security-and-authorization/' | absolute_url }}). Notably, `PATCH /orders/{id}/status` requires **ADMIN** only.
- **Ownership:** On create, the JWT **`sub`** is stored as `ownerSubject`. Reads and cancel (non-admin) are scoped to that owner.
- **Filter chain order (matches `SecurityConfig`):** `BearerTokenAuthenticationFilter` validates the JWT and builds the security context **first**; `RateLimitingFilter` is registered **immediately after** it (`addFilterAfter(rateLimitingFilter, BearerTokenAuthenticationFilter.class)`). URL authorization (`authorizeHttpRequests`) runs later in the same chain, before the controller. Diagrams below show **JWT → rate limit → controller** in that order.
- **Rate limiting:** Redis token bucket (with sliding-window fallback when configured); fail-open if Redis is unavailable for the primary script path.
- **Request correlation:** `RequestContextFilter` is a separate servlet filter (`@Component`); it sets `requestId` / region in MDC for logs and `ApiError` (exact ordering vs. security is managed by Spring Boot filter registration).

---

## POST `/orders` — Create order (optional idempotency)

Synchronous commit includes **regional write gate**, **global idempotency** (Redis), **single DB transaction** (order + outbox row), **cache invalidation**, and **after-commit idempotency completion**. `ORDER_CREATED` publication is **asynchronous** via the outbox pipeline.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant JWT as BearerTokenAuthenticationFilter
    participant RL as RateLimitingFilter (Redis)
    participant API as OrderController
    participant Service as OrderService
    participant Idemp as GlobalIdempotencyService
    participant RCM as RegionalConsistencyManager
    participant BPM as BackpressureManager
    participant DB as Postgres (orders & outbox)
    participant Cache as Redis (Read Cache)
    participant Outbox as OutboxPublisher / Fetcher / Processor
    participant KEP as KafkaEventPublisher
    participant SR as OrderCreatedEventSchemaRegistry
    participant Kafka as Kafka Cluster

    Note over Client, Kafka: Phase 1: Synchronous API Request

    Client->>JWT: POST /orders (body, X-Idempotency-Key, Authorization Bearer)
    JWT->>JWT: Validate JWT, set SecurityContext
    JWT->>RL: Filter chain continues
    RL->>RL: Token bucket (policy via RedisBackedRateLimitPolicyProvider)
    RL->>API: Allow
    API->>Service: createOrder(request, idempotencyKey, ownerSubject)

    Service->>RCM: allowsWrites() (delegates to RegionalFailoverManager)
    Service->>BPM: shouldRejectWrites()
    Service->>Idemp: resolveState / markInProgress (if key present)

    Service->>Service: Order.create() → PENDING

    rect rgb(230, 240, 255)
        Note over Service, DB: @Transactional: single DB transaction
        Service->>DB: orderRepository.save(OrderRecord)
        Service->>DB: outboxService.enqueueOrderCreated (serialize payload into outbox row)
    end

    Service->>Cache: invalidateOrderCaches (by-id + list keys)

    Note over Service, Idemp: TransactionSynchronization afterCommit
    Service-)Idemp: markCompleted(idempotencyKey, newOrderId)

    Service-->>API: OrderResponse
    API-->>Client: 201 Created

    Note over Outbox, Kafka: Phase 2: Background outbox workers (not the HTTP thread)

    Outbox->>DB: OutboxFetcher: claimBatchForPartition (FOR UPDATE SKIP LOCKED), then IN_FLIGHT + lease
    Outbox->>Outbox: OutboxProcessor.publishOne
    Outbox->>SR: deserialize(payload) — same contract validated at enqueue
    Outbox->>KEP: publishOrderCreated(event)
    KEP->>SR: serialize(event) for Kafka record
    KEP->>Kafka: send async (CompletableFuture completes on broker ACK)
    Kafka-->>Outbox: Success callback
    Outbox->>DB: markSentIfLeased(id, leaseOwner, leaseVersion, …)
    Note right of DB: Row → SENT when fenced update succeeds
```

---

## GET `/orders/{id}` — Get order by id

Read path uses **cache-aside** with **request coalescing** on cache miss. Admin callers use admin-scoped cache keys; non-admins load only their own orders (cross-tenant id → `404`).

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant JWT as BearerTokenAuthenticationFilter
    participant RL as RateLimitingFilter (Redis)
    participant API as OrderController
    participant Query as OrderQueryService
    participant Cache as Redis (Read Cache)
    participant DB as Postgres (orders)

    Client->>JWT: GET /orders/{id} (Authorization Bearer)
    JWT->>JWT: Validate JWT, set SecurityContext
    JWT->>RL: Filter chain continues
    RL->>RL: Token bucket
    RL->>API: Allow (authorizeHttpRequests already passed)
    API->>Query: getById(id, viewerSubject, viewerIsAdmin)

    Query->>Query: Build cache key (admin vs user-scoped)
    Query->>Cache: get(key, OrderResponse)

    alt Cache hit
        Cache-->>Query: Cached OrderResponse
    else Cache miss (coalesced load)
        Query->>Query: synchronized single-flight for key
        Query->>DB: findById (admin) OR findByIdAndOwnerSubject
        DB-->>Query: OrderRecord
        Query->>Query: map to OrderResponse
        Query->>Cache: put(key, response, TTL)
    end

    Query-->>API: OrderResponse
    API-->>Client: 200 OK
```

---

## PATCH `/orders/{id}/status` — Update status (ADMIN, optimistic locking)

**ADMIN-only** endpoint. Enforces **regional write gate**, **backpressure**, loads the aggregate, checks **expected version**, applies a **domain transition**, persists with **optimistic locking** (one retry on conflict), then **invalidates** read caches. **No** outbox row is written for this operation in the current implementation.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant JWT as BearerTokenAuthenticationFilter
    participant RL as RateLimitingFilter (Redis)
    participant Authz as URL authorization (ADMIN only)
    participant API as OrderController
    participant Service as OrderService
    participant RCM as RegionalConsistencyManager
    participant BPM as BackpressureManager
    participant DB as Postgres (orders)
    participant Cache as Redis (Read Cache)

    Client->>JWT: PATCH /orders/{id}/status (body + Bearer)
    JWT->>JWT: Validate JWT, set SecurityContext
    JWT->>RL: Chain continues
    RL->>RL: Token bucket
    RL->>Authz: Chain continues
    alt Not ROLE_ADMIN
        Authz-->>Client: 403 FORBIDDEN (before controller)
    end
    Authz->>API: Allow
    API->>Service: updateStatus(id, status, expectedVersion)

    Service->>RCM: allowsWrites()
    Service->>BPM: shouldRejectWrites()
    Service->>DB: findById(id)
    DB-->>Service: OrderRecord

    Service->>Service: checkExpectedVersion(expected, actual)
    Service->>Service: order.updateStatus(status) (domain rules)

    rect rgb(230, 240, 255)
        Note over Service, DB: Transaction: optimistic lock
        Service->>DB: orderRepository.save (version bump)
    end

    Service->>Cache: invalidateOrderCaches (by-id + list keys)

    Service-->>API: OrderResponse
    API-->>Client: 200 OK
```

---

## GET `/orders` — List orders (optional status filter)

Uses **status-scoped list cache keys** (admin vs user) and **coalescing** on miss. Results are **bounded** by `app.query.list-max-rows` (repository page size).

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant JWT as BearerTokenAuthenticationFilter
    participant RL as RateLimitingFilter (Redis)
    participant API as OrderController
    participant Query as OrderQueryService
    participant Cache as Redis (Read Cache)
    participant DB as Postgres (orders)

    Client->>JWT: GET /orders?status= (Bearer)
    JWT->>JWT: Validate JWT
    JWT->>RL: Chain continues
    RL->>RL: Token bucket
    RL->>API: Allow
    API->>Query: list(status, viewerSubject, viewerIsAdmin)

    Query->>Query: Build list cache key (scope + status)
    Query->>Cache: get(key, CachedOrderList)

    alt Cache hit
        Cache-->>Query: Cached list
    else Cache miss (coalesced load)
        Query->>Query: synchronized single-flight for key
        Query->>DB: findByOwnerSubject / findByStatus / … (bounded page)
        DB-->>Query: OrderRecord rows
        Query->>Cache: put(key, CachedOrderList, TTL)
    end

    Query-->>API: List OrderResponse
    API-->>Client: 200 OK (JSON array)
```

---

## GET `/orders/page` — Paginated list

Same **authorization and scoping** as `GET /orders`, but responses are **paged** (`page`, `size` capped at 500). This path **does not** use the list cache in `OrderQueryService`; each call hits the database (still inside a read-only transaction).

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant JWT as BearerTokenAuthenticationFilter
    participant RL as RateLimitingFilter (Redis)
    participant API as OrderController
    participant Query as OrderQueryService
    participant DB as Postgres (orders)

    Client->>JWT: GET /orders/page?page=&size=&status= (Bearer)
    JWT->>JWT: Validate JWT
    JWT->>RL: Chain continues
    RL->>RL: Token bucket
    RL->>API: Allow
    API->>Query: listPage(status, page, size, viewerSubject, viewerIsAdmin)

    Query->>Query: Normalize page ≥ 0, size ∈ [1, 500]
    Query->>DB: Pageable query (owner or global for admin)
    DB-->>Query: Page OrderRecord

    Query->>Query: Map to PagedOrderResult (items + totals)
    Query-->>API: PagedOrderResult
    API-->>Client: 200 OK (JSON)
```

---

## PATCH `/orders/{id}/cancel` — Cancel order

Enforces **write gates**, **ownership** (or **ADMIN** bypass), **regional consistency** check (`RegionalConsistencyManager.shouldApplyIncomingUpdate`), **domain cancel**, **optimistic locking** with retry, then **cache invalidation**.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant JWT as BearerTokenAuthenticationFilter
    participant RL as RateLimitingFilter (Redis)
    participant API as OrderController
    participant Service as OrderService
    participant RCM as RegionalConsistencyManager
    participant BPM as BackpressureManager
    participant DB as Postgres (orders)
    participant Cache as Redis (Read Cache)

    Client->>JWT: PATCH /orders/{id}/cancel (Bearer)
    JWT->>JWT: Validate JWT
    JWT->>RL: Chain continues
    RL->>RL: Token bucket
    RL->>API: Allow
    API->>Service: cancel(id, callerSubject, callerIsAdmin)

    Service->>RCM: allowsWrites()
    Service->>BPM: shouldRejectWrites()
    Service->>DB: findById(id)
    DB-->>Service: OrderRecord

    Service->>Service: assertCancelAllowed (owner vs ADMIN)
    Service->>RCM: shouldApplyIncomingUpdate (regional conflict guard)

    Service->>Service: order.cancel()

    rect rgb(230, 240, 255)
        Note over Service, DB: Transaction: optimistic lock
        Service->>DB: orderRepository.save
    end

    Service->>Cache: invalidateOrderCaches

    Service-->>API: OrderResponse
    API-->>Client: 200 OK
```

---

## Related asynchronous flows (not HTTP controllers)

Background behavior (outbox retry, scheduler `PENDING`→`PROCESSING`, Kafka consumer dedupe) is documented with failure-oriented diagrams in [Failure Scenarios]({{ '/failure-scenarios/' | absolute_url }}) and in [Design and Architecture]({{ '/design-and-architecture/' | absolute_url }}).
