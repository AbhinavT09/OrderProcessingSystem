# Domain Layer Reference

## Aggregate and value objects

### `Order`
- Lifecycle methods: `updateStatus`, `cancel`, `promotePendingToProcessing`.
- `rehydrate(...)` reconstructs aggregate from persistence state with version and idempotency key.

### `OrderItem`
- Immutable item value record (`productName`, `quantity`, `price`).

### `OrderStatus`
- Lifecycle enum used by state strategy and persistence mapping.

## State strategy
- `OrderState` contract + `AbstractOrderState` default transition safeguards.
- `PendingState`, `ProcessingState`, `ShippedState`, `DeliveredState`, `CancelledState` encode legal transitions.
- `OrderStateFactory` maps enum status to concrete strategy implementation.
