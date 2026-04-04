# OrderProcessingSystem

Production-ready Java Spring Boot backend for e-commerce order processing.

## Features
- Create, fetch, list, update, and cancel orders
- State-pattern based order lifecycle: `PENDING -> PROCESSING -> SHIPPED -> DELIVERED`
- Event-driven delayed processing with Kafka (`OrderCreated` flow)
- Idempotency support for order creation via `X-Idempotency-Key`
- Optimistic locking for concurrent updates
- JWT authentication and role-based authorization
- Request validation, rate limiting, structured logging, metrics, and tracing

## Tech Stack
- Java 17, Spring Boot 3
- Spring Web, Spring Data JPA, Spring Security
- Kafka, H2 (runtime), Micrometer/Prometheus, OpenTelemetry

## Run
```bash
mvn clean test
mvn spring-boot:run
```

## API
- `POST /orders`
- `GET /orders/{id}`
- `GET /orders?status=...`
- `PATCH /orders/{id}/status`
- `PATCH /orders/{id}/cancel`

## Documentation
- Project docs index (GitHub Pages): [docs/index.md](./docs/index.md)
