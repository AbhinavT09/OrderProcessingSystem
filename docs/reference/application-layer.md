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

- Enforces regional write gating via `RegionalFailoverManager` and backpressure via `BackpressureManager`
- Implements idempotency lifecycle flow:
  - resolve key state (`IN_PROGRESS`/`COMPLETED`/absent)
  - reserve `IN_PROGRESS`
  - persist order + outbox
  - mark `COMPLETED` after commit
- **Create:** binds **`owner_subject`** from caller (`Order.create(..., ownerSubject)`); rejects blank owner
- Uses optimistic locking and retry semantics for status/cancel mutations
- **Cancel:** `assertCancelAllowed` — admins always; non-admins require `owner_subject == caller`; null-owner legacy rows require admin (`ForbiddenException`)
- **Scheduled promotion:** `promotePendingOrdersScheduled()` loads `PENDING` orders in **bounded pages** (`findByStatus` + `Pageable`), promotes **one short transaction per order**, looping until no `PENDING` remain; invoked by `PendingToProcessingScheduler`
- Invalidates read cache via **`OrderReadCacheKeys`** + legacy `order:id:` eviction (see below)

### `OrderQueryService` (query/read side)

- Cache-aside for by-id and status-filtered lists with **stampede coalescing**
- **Scoped reads:**
  - **Admin:** `findById`, `findAll` / `findByStatus` (global)
  - **Non-admin:** `findByIdAndOwnerSubject`, `findByOwnerSubject` / `findByOwnerSubjectAndStatus`
- Cross-tenant access by id → **`NotFoundException`** (404) for non-admins
- Cache keys from **`OrderReadCacheKeys`** — never share a by-id key between users
- Emits query timing/error metrics (`orders.query.*`)

### `OrderReadCacheKeys` (companion utility)

- Centralizes key shapes: `order:admin:id:*`, `order:user:{sub}:id:*`, list keys for admin vs per-owner
- Used by `OrderQueryService` (read) and invalidation in `OrderService` (write)

### `OutboxService`

- Creates `OutboxEntity` rows with:
  - event payload from schema registry
  - initial `PENDING` status
  - partition key for publisher sharding

## Ports

- **`OrderRepository`:** CRUD and queries including **owner-scoped** methods (`findByIdAndOwnerSubject`, `findByOwnerSubject`, `findByOwnerSubjectAndStatus`)
- **`OutboxRepository`:** outbox persistence/claim/archive contract
- **`ProcessedEventRepository`:** consumer dedupe marker contract
- **`EventPublisher`:** async integration event publication contract
- **`CacheProvider`:** cache-aside operations contract

## Key Application Invariants

- Writes are blocked when regional mode is passive or backpressure rejects writes
- Order create does not directly publish Kafka events (outbox only)
- Idempotency completion is recorded only after successful commit
- Query reads remain available during cache failures (fail-soft to DB)
- **Authorization** for reads/cancels is enforced in application services, not only in `SecurityConfig`

## Related documentation

- [Security and Authorization]({{ '/security-and-authorization/' | absolute_url }}) — end-to-end auth matrix and cache rules
- [API Layer]({{ '/reference/api-layer/' | absolute_url }}) — HTTP mapping
