# Folder and Class Reference

## Source Layout

- `src/main/java/com/example/orderprocessing/interfaces/http`
- `src/main/java/com/example/orderprocessing/application`
- `src/main/java/com/example/orderprocessing/domain/order`
- `src/main/java/com/example/orderprocessing/infrastructure`
- `src/main/java/com/example/orderprocessing/config`

## Documentation Entry Points

- [Reference Index](./reference/index.md)
- [Interface HTTP Layer](./reference/api-layer.md)
- [Application Layer](./reference/application-layer.md)
- [Domain Layer](./reference/domain-layer.md)
- [Infrastructure Layer](./reference/infrastructure-layer.md)
- [Configuration and Runtime Controls](./reference/configuration-and-runtime.md)

## Quick Responsibility Map

- **Interfaces**: endpoint contracts, request binding, error translation
- **Application**: use-case orchestration, transactions, port boundaries
- **Domain**: lifecycle invariants and state transitions
- **Infrastructure**: adapters (DB/Kafka/Redis/security/resilience)
- **Config**: runtime wiring and dependency-client setup
