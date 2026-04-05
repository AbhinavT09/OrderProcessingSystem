---
title: Application Layer
parent: Reference
nav_order: 2
---

# Application Layer Reference

## Layer Purpose

Application services orchestrate use-cases, transactions, and infrastructure ports while keeping domain logic and transport concerns separate.

## Service Responsibilities

### `OrderService` (command/write side)

- Enforces regional write gating via `RegionalFailoverManager`
- Implements idempotency lifecycle flow:
  - resolve key state (`IN_PROGRESS`/`COMPLETED`/absent)
  - reserve `IN_PROGRESS`
  - persist order + outbox
  - mark `COMPLETED` after commit
- Uses optimistic locking and retry semantics for status/cancel mutations
- Invalidates read cache keys after successful writes

### `OrderQueryService` (query/read side)

- Cache-aside read orchestration for by-id and filtered-list calls
- Coalesces concurrent cache misses by key to avoid thundering herd
- Emits query timing/error metrics
- Falls back to repository results when cache is degraded

### `OutboxService`

- Creates `OutboxEntity` rows with:
  - event payload from schema registry
  - initial `PENDING` status
  - partition key for publisher sharding

## Ports

- `OrderRepository`: order persistence contract over application snapshots (`OrderRecord`, `OrderItemRecord`) rather than infrastructure entities
- `OutboxRepository`: outbox persistence/claim/archive contract
- `ProcessedEventRepository`: consumer dedupe marker contract
- `EventPublisher`: async integration event publication contract
- `CacheProvider`: cache-aside operations contract

## Key Application Invariants

- Writes are blocked when regional mode is passive
- Order create does not directly publish Kafka events
- Idempotency completion is recorded only after successful commit
- Query reads remain available during cache failures
