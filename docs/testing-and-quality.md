# Testing and Quality Strategy

## Test layers

### Domain tests
- `OrderAggregateTest`
- Verifies state machine invariants and transition rules.

### API integration tests
- `OrderControllerIntegrationTest`
- Validates auth/rbac/validation contracts and endpoint behavior.

### Contract tests
- `OrderCreatedEventContractTest`
- Ensures event schema compatibility over time.

### Consumer behavior tests
- `OrderCreatedConsumerUnitTest`
- Verifies delayed promotion and processed-marker write.

## Failure-oriented test recommendations (next additions)

To fully mirror runtime resilience, add:

1. **Outbox retry tests**
   - publish failure increments retry count and leaves status FAILED
   - success transition to SENT

2. **Rate limiter Redis script tests**
   - token-bucket allows within limit, blocks beyond capacity
   - key structure contains user/path/ip dimensions

3. **Consumer ack tests**
   - ack called only after successful process
   - no ack on retry-throwing paths

4. **DLQ handler tests**
   - verifies metric increment and enriched log context fields

5. **Cache TTL tests**
   - ID cache uses 5 min TTL
   - list cache uses 1 min TTL

## Quality principles

- Correctness-first writes (idempotency + optimistic locking + outbox)
- Eventual consistency for async transitions with bounded retries
- Degrade gracefully on cache/redis outages
- Observable by default (logs/metrics/traces)

