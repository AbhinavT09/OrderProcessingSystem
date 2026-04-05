package com.example.orderprocessing.infrastructure.messaging.schema;

import com.example.orderprocessing.application.event.OrderCreatedEvent;

/**
 * Port-like contract for event schema operations on {@code ORDER_CREATED}.
 *
 * <p><b>Architecture role:</b> infrastructure abstraction used by producer and consumer adapters
 * to keep serialization, deserialization, and compatibility rules centralized.</p>
 *
 * <p><b>Idempotency and resilience context:</b> deterministic payload normalization keeps
 * repeated serialization stable, while strict validation prevents malformed events from entering
 * retry loops.</p>
 *
 * <p><b>Transaction boundary:</b> schema validation can be invoked in DB transaction contexts
 * (outbox enqueue) and in Kafka consume/publish paths.</p>
 */
public interface OrderCreatedEventSchemaRegistry {
    /**
     * Performs latestSchemaVersion.
     * @return operation result
     */
    int latestSchemaVersion();

    /**
     * Performs serialize.
     * @param event input argument used by this operation
     * @return operation result
     */
    String serialize(OrderCreatedEvent event);

    /**
     * Performs deserialize.
     * @param payload input argument used by this operation
     * @return operation result
     */
    OrderCreatedEvent deserialize(String payload);

    /**
     * Performs validate.
     * @param event input argument used by this operation
     */
    void validate(OrderCreatedEvent event);
}
