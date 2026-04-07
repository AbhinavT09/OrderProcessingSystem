---
title: Domain Layer
parent: Reference
nav_order: 3
---

# Domain Layer Reference

## Package Location

- `domain/order` for aggregate/value types.
- `domain/order/state` for state strategy hierarchy.

## Aggregate and Value Objects

### `Order`

- Aggregate root that owns lifecycle transitions and cancellation semantics
- Carries **`ownerSubject`** (who placed the order; mirrored in persistence as `owner_subject`) for application-level authorization — the domain does not interpret JWTs itself
- Creation entry points:
  - `create(items, idempotencyKey, ownerSubject)` for new aggregate construction
  - `rehydrate(..., ownerSubject, ...)` for persistence reconstruction
- Behavior methods:
  - `updateStatus(...)`
  - `cancel()`
  - `promotePendingToProcessing()`

### `OrderItem`

- Immutable value object (`productName`, `quantity`, `price`)

### `OrderStatus`

- Canonical lifecycle statuses used by state objects and persistence mapping

## State Pattern Structure

- `OrderState`: transition/cancel/promotion contract
- `AbstractOrderState`: common transition lookup and default conflict behavior
- `OrderStateFactory`: maps `OrderStatus` to concrete state implementation
- Concrete states:
  - `PendingState`
  - `ProcessingState`
  - `ShippedState`
  - `DeliveredState`
  - `CancelledState`

## Transition Rules

- Valid transitions are defined in concrete state maps
- Invalid transitions throw `ConflictException`
- `cancel()` is valid only from pending state
- delivered/cancelled are terminal states for further transitions
