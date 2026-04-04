# Testing and Quality Strategy

## Test layers implemented

### 1) Domain unit tests

File: `src/test/java/com/example/orderprocessing/domain/model/OrderAggregateTest.java`

Coverage:

- Valid lifecycle transitions
- Invalid transitions rejected with conflict
- Cancel behavior constraint (pending-only)
- Pending promotion semantics

### 2) API integration tests

File: `src/test/java/com/example/orderprocessing/api/OrderControllerIntegrationTest.java`

Coverage:

- Authentication required (`401`)
- Authorized create and fetch flow
- RBAC authorization behavior (`403`)
- Validation behavior for headers/payload (`400`)

Notes:

- Uses `MockMvc` with Spring context.
- Uses Spring Security test JWT request post-processors.
- Kafka auto-start/admin creation disabled for deterministic API tests.

### 3) Kafka contract tests

File: `src/test/java/com/example/orderprocessing/infrastructure/messaging/OrderCreatedEventContractTest.java`

Coverage:

- Producer payload field presence and naming contract
- Consumer-side deserialization compatibility
- Invalid payload rejection

### 4) Consumer behavior unit test

File: `src/test/java/com/example/orderprocessing/infrastructure/messaging/OrderCreatedConsumerUnitTest.java`

Coverage:

- Promotion to `PROCESSING` when delayed threshold passed
- Processed marker persistence for dedupe

## Build command

```bash
mvn clean test
```

## Quality principles used

- Domain invariants are validated at aggregate/state layer.
- API behavior is validated at HTTP boundary.
- Event schema is validated as a contract (not only implementation detail).
- Integration tests avoid brittle infra dependencies where possible.
