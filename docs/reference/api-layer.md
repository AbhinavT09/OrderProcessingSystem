---
title: API Layer
parent: Reference
nav_order: 1
---

# Interface HTTP Layer Reference

## Package Location

`src/main/java/com/example/orderprocessing/interfaces/http`

## Controllers

### `OrderController`

#### Endpoints

- `POST /orders`
  - Inputs: `CreateOrderRequest`, optional `X-Idempotency-Key`, authenticated principal (`Authentication.getName()` → JWT `sub` stored as order owner)
  - Delegates to: `OrderService.createOrder(...)`
  - Notes: repeated key may return completed order or conflict on in-progress duplicate
- `GET /orders/{id}`
  - Delegates to: `OrderQueryService.getById(id, viewerSubject, viewerIsAdmin)`
  - Notes: returns `404` when the id does not exist **or** when a non-admin caller is not the stored owner (same message avoids leaking existence)
- `GET /orders?status=<STATUS>`
  - Delegates to: `OrderQueryService.list(status, viewerSubject, viewerIsAdmin)`
  - Notes: missing status means all orders **visible to the caller**; non-admins only see their own orders
- `GET /orders/page`
  - Delegates to: `OrderQueryService.listPage(status, page, size, viewerSubject, viewerIsAdmin)`
  - Notes: same visibility rules as `GET /orders` (non-admin: own orders only; admin: global)
- `PATCH /orders/{id}/status`
  - Inputs: `UpdateOrderStatusRequest(status, version)`
  - Delegates to: `OrderService.updateStatus(...)`
  - Notes: optimistic version conflict returns `409`
- `PATCH /orders/{id}/cancel`
  - Delegates to: `OrderService.cancel(...)` with caller subject and admin flag
  - Notes: domain allows cancel only from `PENDING`; **authorization** — non-admins may cancel only orders they created (matching stored owner subject); admins may cancel any order

## DTO and Error Contracts

### Request DTOs

- `CreateOrderRequest`: non-empty item collection
- `OrderItemRequest`: product/quantity/price validation contract
- `UpdateOrderStatusRequest`: target status + expected version for optimistic concurrency

### Response DTOs

- `OrderResponse`: id, status, created time, item list
- `ApiError`: standardized error envelope (`code`, `message`, `requestId`, `timestamp`)

## DTO-to-Domain Mapping Rules

Interface DTOs are intentionally thin; all invariants are enforced in domain/application layers.

| Interface DTO | Mapped Domain Concept | Rule Source | Notes |
|---|---|---|---|
| `CreateOrderRequest` | `Order.create(items, idempotencyKey, ownerSubject)` | `domain/order/Order` | Creation always starts in `PENDING`; idempotency key and JWT subject (owner) are carried into aggregate snapshot |
| `OrderItemRequest` | `OrderItem` value object | `domain/order/OrderItem` | Quantity/price shape validated at API boundary; business transitions validated in state objects |
| `UpdateOrderStatusRequest.status` | `Order.updateStatus(status)` | `domain/order/state/*` | Illegal transitions throw `ConflictException` |
| `UpdateOrderStatusRequest.version` | optimistic concurrency token | `OrderService.checkExpectedVersion(...)` | Enforces compare-and-set semantics before mutation |
| `OrderResponse.status` | aggregate lifecycle state | `OrderStateFactory` + concrete states | Read model reflects eventual consistency from async consume path |

### Why this mapping exists

- Keeps transport contracts stable while domain logic evolves.
- Prevents controller-layer duplication of domain transition rules.
- Makes optimistic concurrency explicit at the API boundary.
- Reduces misinterpretation between "validation error" and "state conflict" failure modes.

### Exception Mapping (`GlobalExceptionHandler`)

- `NotFoundException` -> `404 NOT_FOUND`
- `ConflictException` -> `409 CONFLICT`
- `ForbiddenException` -> `403 FORBIDDEN` (e.g. cancel by non-owner)
- `InfrastructureException` -> `503 SERVICE_UNAVAILABLE`
- validation and malformed payload failures -> `400 BAD_REQUEST`
- uncaught exceptions -> `500 UNEXPECTED_ERROR` (logged at `ERROR`, counter `api.errors.unexpected`)

## Package Structure

- `interfaces/http/controller`
- `interfaces/http/dto`
- `interfaces/http/error`
- `interfaces/http/exception`

## Related documentation

- [Security and Authorization]({{ '/security-and-authorization/' | absolute_url }}) — full matrix of roles, ownership, `404` vs `403`, and cache isolation
- [Application Layer]({{ '/reference/application-layer/' | absolute_url }}) — how `OrderQueryService` / `OrderService` enforce scopes
