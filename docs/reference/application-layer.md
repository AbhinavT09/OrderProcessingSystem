# Application Layer Reference

## Service split

### `OrderService` (command/write side)
- Enforces regional write gate via `RegionalFailoverManager.allowsWrites()`.
- Uses crosscutting `GlobalIdempotencyService` lock + completed mapping to prevent cross-region duplicate creation.
- Writes `OrderEntity` and outbox event through `OutboxService` (no direct Kafka call in write transaction).
- Invalidates cache entries after writes (`order:id:*` and status lists).

### `OrderQueryService` (query/read side)
- Cache-aside reads for by-id and list queries.
- Coalesces cache misses with key-scoped lock map to reduce stampede.
- Falls back to repository path when cache operations degrade.

### `OutboxService`
- Creates `OutboxEntity` with `PENDING` status and serialized event payload via schema registry.

## Ports
- `OrderRepository`, `OutboxRepository`, `ProcessedEventRepository`, `EventPublisher`, `CacheProvider` define inbound contracts for infrastructure adapters.
