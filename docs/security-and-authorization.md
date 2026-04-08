---
title: Security and Authorization
nav_order: 7
---

# Security and Authorization

This document is the **authoritative** description of authentication, roles, resource ownership, and how API responses avoid information leaks. It complements the [API layer reference]({{ '/reference/api-layer/' | absolute_url }}) and [configuration reference]({{ '/reference/configuration-and-runtime/' | absolute_url }}).

## 1. Authentication

- **Mechanism:** OAuth2 Resource Server with **JWT** bearer tokens (`Authorization: Bearer <token>`).
- **Decoder:** HMAC secret from `app.security.jwt-secret` (must be overridden in production; never deploy default secrets).
- **Identity:** The Spring Security principal name is the JWT **`sub`** claim (`JwtAuthenticationToken` uses `jwt.getSubject()` as the principal name).
- **Roles:** A `roles` claim (string array) is mapped to Spring authorities as `ROLE_<name>` via `RoleClaimJwtAuthenticationConverter` (e.g. `["USER","ADMIN"]` → `ROLE_USER`, `ROLE_ADMIN`).

## 2. Role matrix (HTTP layer)

| Endpoint | Required roles | Notes |
|----------|----------------|--------|
| `POST /orders` | `USER` or `ADMIN` | |
| `GET /orders/{id}` | `USER` or `ADMIN` | Read scope enforced in application layer (see §4) |
| `GET /orders` | `USER` or `ADMIN` | Optional `?status=` filter; read scope in application layer (§4) |
| `GET /orders/page` | `USER` or `ADMIN` | Same |
| `PATCH /orders/{id}/status` | `ADMIN` only | Operational status changes |
| `PATCH /orders/{id}/cancel` | `USER` or `ADMIN` | Domain + ownership rules in §5 |

Unauthenticated requests receive **401**; authenticated but insufficient role receives **403** (Spring Security).

## 3. Order ownership model

- On **create**, the service persists **`owner_subject`** = authenticated principal name (JWT `sub`) on the order row (`OrderEntity.ownerSubject` ↔ `OrderRecord.ownerSubject` ↔ domain `Order.getOwnerSubject()`).
- **Idempotency:** Replays with the same `X-Idempotency-Key` return the existing order regardless of caller; clients should use keys scoped to a user/session to avoid cross-user reuse.

## 4. Read authorization (enforced in `OrderQueryService`)

Goals:

1. **Users** must only **list** and **get-by-id** orders they placed.
2. **Admins** may read **all** orders (support, operations).
3. **Cross-tenant GET by id** must not reveal whether an id exists (**enumeration resistance**).

### Behavior

| Caller | `GET /orders/{id}` | `GET /orders` / `GET /orders/page` |
|--------|----------------------|-------------------------------------|
| **ADMIN** | Load by id (global) | All orders (subject to pagination / `list-max-rows`) |
| **Non-admin** | `findByIdAndOwnerSubject(id, sub)` | `findByOwnerSubject` / `findByOwnerSubjectAndStatus` |

- If a non-admin requests another user’s id: **`404 NOT_FOUND`** with the same message as a missing id (`NotFoundException`). This is intentional: **403** would confirm existence.
- **Legacy rows** with `owner_subject` null: non-admins **cannot** resolve them by id (no match on owner); **admins** can still load by id. Plan a data migration if legacy data must be user-visible.

### Cache isolation

Read models use **`OrderReadCacheKeys`**:

- Admin: `order:admin:id:{uuid}`, `orders:status:...` (global list keys).
- User: `order:user:{sub}:id:{uuid}`, `orders:user:{sub}:status:...`.

Writes in **`OrderService`** invalidate legacy `order:id:{uuid}`, both admin and user-scoped by-id keys, and both global and per-owner list keys for all statuses so stale cross-tenant entries cannot be served.

## 5. Cancel authorization (enforced in `OrderService`)

- **Domain:** Only **`PENDING`** orders can be cancelled (`PendingState`); other states yield **`409 CONFLICT`**.
- **Application:**
  - **ADMIN:** may cancel any order (subject to domain rules).
  - **Non-admin:** may cancel only if `order.ownerSubject` equals caller `sub`.
  - **Legacy null owner:** non-admins receive **`403 FORBIDDEN`** with a message that admin is required; admins may cancel.

## 6. Error contract (relevant codes)

| HTTP | `ApiError.code` | Typical cause |
|------|-----------------|----------------|
| 401 | `UNAUTHORIZED` | Missing/invalid token |
| 403 | `FORBIDDEN` | Wrong role **or** forbidden cancel (ownership) |
| 404 | `NOT_FOUND` | Unknown order **or** not visible to caller (scoped GET) |
| 409 | `CONFLICT` | Idempotency, version mismatch, illegal transition |
| 503 | `INFRASTRUCTURE_ERROR` | Region passive, backpressure, dependency failure |
| 500 | `UNEXPECTED_ERROR` | Unhandled exception; logged and metered (`api.errors.unexpected`) |

## 7. Operational checklist

- [ ] `JWT_SECRET` / `app.security.jwt-secret` set from a secret manager in every non-dev environment.
- [ ] Alerts on **`api.errors.unexpected`** and DLQ / outbox backlog metrics.
- [ ] Confirm **integration tests** use explicit `ROLE_*` on mock JWTs; the test `jwt()` helper does not run `RoleClaimJwtAuthenticationConverter` on claims — authorities are set explicitly in tests (see `OrderControllerIntegrationTest`).
