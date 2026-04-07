package com.example.orderprocessing.domain.order;

import com.example.orderprocessing.application.exception.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAggregateTest {

    @Test
    void shouldAllowValidLifecycleTransitions() {
        Order order = Order.rehydrate(
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                List.of(new OrderItem("Keyboard", 1, 99.0)),
                OrderStatus.PENDING,
                0L);

        order.updateStatus(OrderStatus.PROCESSING);
        order.updateStatus(OrderStatus.SHIPPED);
        order.updateStatus(OrderStatus.DELIVERED);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void shouldRejectInvalidTransitionFromDelivered() {
        Order order = Order.rehydrate(
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                List.of(new OrderItem("Mouse", 1, 20.0)),
                OrderStatus.DELIVERED,
                0L);

        assertThrows(ConflictException.class, () -> order.updateStatus(OrderStatus.PROCESSING));
    }

    @Test
    void shouldAllowCancelOnlyWhenPending() {
        Order pending = Order.rehydrate(
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                List.of(new OrderItem("Monitor", 1, 199.0)),
                OrderStatus.PENDING,
                0L);
        pending.cancel();
        assertEquals(OrderStatus.CANCELLED, pending.getStatus());

        Order shipped = Order.rehydrate(
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                List.of(new OrderItem("Cable", 2, 9.0)),
                OrderStatus.SHIPPED,
                0L);
        assertThrows(ConflictException.class, shipped::cancel);
    }

    @Test
    void shouldPromotePendingOnlyOnce() {
        Order order = Order.rehydrate(
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                List.of(new OrderItem("Dock", 1, 49.0)),
                OrderStatus.PENDING,
                0L);

        assertTrue(order.promotePendingToProcessing());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertFalse(order.promotePendingToProcessing());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
    }
}
