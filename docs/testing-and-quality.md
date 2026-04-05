---
title: Testing and Quality
nav_order: 5
---

# Testing and Quality

## 1. Testing Objectives

- validate correctness of domain transitions and write invariants
- verify idempotency lifecycle semantics under retries/concurrency
- prove outbox/event flow reliability under failure conditions
- ensure API contracts remain stable for success and error paths
- verify degradation behavior for Redis/Kafka/regional dependencies

## 2. Current Test Coverage (Implemented)

### Domain layer

- `OrderAggregateTest`
  - valid transition paths
  - invalid transition conflicts
  - cancellation invariants

### Application layer

- `OrderServiceIdempotencyLifecycleTest`
  - same key same outcome
  - in-progress duplicate rejection and safe retry
  - completed reuse behavior
  - duplicate prevention under concurrent retries
- `OutboxServiceTest`
  - outbox row creation shape and defaults

### Messaging/outbox components

- `OutboxProcessorTest`
- `OutboxFetcherTest`
- `OutboxPublisherTest`
- `KafkaEventPublisherTest`
- `OrderCreatedConsumerUnitTest`
- `OutboxRetryHandlerTest`

Coverage focus:

- async publish result handling
- atomic outbox lease transitions (`IN_FLIGHT`) at claim time
- lease-fenced finalization (`markSentIfLeased`/`markFailedIfLeased`) to prevent stale worker overwrite
- retry transitions, adaptive classification, and scheduling metadata
- consumer dedupe and delayed processing behavior
- transactional Kafka publish path and circuit-open behavior

### Infrastructure/resilience

- `RedisCacheProviderTest`
- `RateLimitingFilterTest`
- `RegionalFailoverManagerTest`

Coverage focus:

- cache hit/miss/degraded behavior
- limiter allow/block with dynamic policy input and fail-open fallback
- active/passive switching and write gating signals

### API integration

- `OrderControllerIntegrationTest`
  - create/read/status/cancel route behavior
  - idempotency behavior at HTTP boundary
  - validation and error contract behavior

## 3. Critical Test Flows

```mermaid
flowchart TD
    F[OutboxFetcherTest] --> F1[Claimed rows transition to IN_FLIGHT]
    F --> F2[Lease nextAttemptAt persisted before processing]
    A[OutboxRetryHandlerTest] --> A1[Transient classification schedules nextAttemptAt]
    A --> A2[Permanent classification terminalizes row]
    B[KafkaEventPublisherTest] --> B1[Transactional publish success]
    B --> B2[Circuit opens after repeated transaction failures]
    C[RateLimitingFilterTest] --> C1[Token bucket allow path]
    C --> C2[429 block path]
    D[OrderCreatedConsumerUnitTest] --> D1[Dedupe marker + state promotion]
```

## 4. Test Design Standards

- deterministic and independent test execution
- meaningful assertions on business outcomes, not implementation details
- minimal over-mocking of core orchestration logic
- integration tests for cross-component critical paths

## 5. Residual Gaps / Next Expansion

- embedded Kafka end-to-end verification (outbox -> producer -> consumer)
- crash-injection integration around outbox lease expiry/reclaim path
- deterministic active-recovery-to-active integration with controlled dependency health
- conflict resolution strategy permutations under concurrent active-active writes
- backpressure-level transitions driving write admission and dynamic throttling
- paginated API contract tests for `GET /orders/page` with size cap and status filter behavior

## 7. Principal Reliability Test Matrix

### P0 contract scenarios

- stale callback rejection: old lease owner/version must fail state transition after reclaim
- async completion bounding: configured in-flight publish cap must hold under slow broker acks
- listener liveness: consumer retry path must not block poll loop threads

### P1/P2 operational scenarios

- bounded list behavior at high data volume for `/orders` and `/orders/page`
- backpressure transition correctness (`NORMAL` -> `ELEVATED` -> `CRITICAL`) and write gating
- DLQ growth alerts and replay readiness validation

## 6. Quality Gates

- `mvn clean compile` must pass
- targeted reliability suites should pass before broad runs
- API and idempotency regression tests are mandatory for write-path changes

