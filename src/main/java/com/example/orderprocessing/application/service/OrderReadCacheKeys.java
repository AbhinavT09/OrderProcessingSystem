package com.example.orderprocessing.application.service;

import com.example.orderprocessing.domain.order.OrderStatus;
import java.util.UUID;

/**
 * Centralizes read-model cache key shapes for order queries (admin vs owner-scoped).
 */
public final class OrderReadCacheKeys {

    private OrderReadCacheKeys() {
    }

    /** @deprecated use {@link #orderByIdAdmin} / {@link #orderByIdUser}; kept for evicting legacy entries */
    public static String legacyOrderById(UUID id) {
        return "order:id:" + id;
    }

    public static String orderByIdAdmin(UUID id) {
        return "order:admin:id:" + id;
    }

    public static String orderByIdUser(String ownerSubject, UUID id) {
        return "order:user:" + ownerSubject + ":id:" + id;
    }

    public static String listByStatusAdmin(OrderStatus status) {
        return "orders:status:" + (status == null ? "ALL" : status.name());
    }

    public static String listByStatusUser(String ownerSubject, OrderStatus status) {
        return "orders:user:" + ownerSubject + ":status:" + (status == null ? "ALL" : status.name());
    }
}
