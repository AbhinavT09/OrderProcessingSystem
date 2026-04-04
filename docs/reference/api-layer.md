# Interface HTTP Layer Reference

## Package Location

`src/main/java/com/example/orderprocessing/interfaces/http`

## Controllers

### `OrderController`

#### Endpoints

- `POST /orders`
  - Inputs: `CreateOrderRequest`, optional `X-Idempotency-Key`
  - Delegates to: `OrderService.createOrder(...)`
  - Notes: repeated key may return completed order or conflict on in-progress duplicate
- `GET /orders/{id}`
  - Delegates to: `OrderQueryService.getById(...)`
  - Notes: returns not-found when id does not exist
- `GET /orders?status=<STATUS>`
  - Delegates to: `OrderQueryService.list(...)`
  - Notes: missing status means unfiltered list
- `PATCH /orders/{id}/status`
  - Inputs: `UpdateOrderStatusRequest(status, version)`
  - Delegates to: `OrderService.updateStatus(...)`
  - Notes: optimistic version conflict returns `409`
- `PATCH /orders/{id}/cancel`
  - Delegates to: `OrderService.cancel(...)`
  - Notes: cancellation allowed only for valid domain states

## DTO and Error Contracts

### Request DTOs

- `CreateOrderRequest`: non-empty item collection
- `OrderItemRequest`: product/quantity/price validation contract
- `UpdateOrderStatusRequest`: target status + expected version for optimistic concurrency

### Response DTOs

- `OrderResponse`: id, status, created time, item list
- `ApiError`: standardized error envelope (`code`, `message`, `requestId`, `timestamp`)

### Exception Mapping (`GlobalExceptionHandler`)

- `NotFoundException` -> `404 NOT_FOUND`
- `ConflictException` -> `409 CONFLICT`
- `InfrastructureException` -> `503 SERVICE_UNAVAILABLE`
- validation and malformed payload failures -> `400 BAD_REQUEST`
- uncaught exceptions -> `500 UNEXPECTED_ERROR`

## Package Structure

- `interfaces/http/controller`
- `interfaces/http/dto`
- `interfaces/http/error`
- `interfaces/http/exception`
