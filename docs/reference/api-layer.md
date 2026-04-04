# API Layer Reference

## Controllers

### `OrderController`
- `POST /orders`: delegates to `OrderService.createOrder()` with optional `X-Idempotency-Key`.
- `GET /orders/{id}`: delegates to `OrderQueryService.getById()`.
- `PATCH /orders/{id}/status`: admin path for explicit status transition with optimistic-version input.
- `GET /orders`: optional status filtering (`null` means full ordered list).
- `PATCH /orders/{id}/cancel`: explicit cancel transition.

## DTOs and error envelope
- `CreateOrderRequest`, `OrderItemRequest`, `UpdateOrderStatusRequest`, `OrderResponse` define transport contracts.
- `ApiError` is the stable error schema (`code`, `message`, `requestId`, `timestamp`).
- `GlobalExceptionHandler` maps domain/infrastructure exceptions to deterministic HTTP status codes.
