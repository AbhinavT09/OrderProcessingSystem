package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.port.OrderItemRecord;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.interfaces.http.dto.OrderItemRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.domain.order.Order;
import com.example.orderprocessing.domain.order.OrderItem;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderItemEmbeddable;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
/**
 * Application-layer mapping component across interface, domain, and persistence models.
 *
 * <p>Concentrates translation rules so services remain focused on orchestration and domain intent.</p>
 */
public class OrderMapper {

    /**
     * Converts request items into domain value objects.
     *
     * @param items incoming HTTP DTO items
     * @return domain items used by aggregate construction
     */
    public List<OrderItem> toDomainItems(List<OrderItemRequest> items) {
        return items.stream().map(item -> new OrderItem(item.productName(), item.quantity(), item.price())).toList();
    }

    /**
     * Rehydrates a domain aggregate from persistence representation.
     *
     * @param record persistence-neutral order snapshot
     * @return domain aggregate preserving status/version semantics
     */
    public Order toDomain(OrderRecord record) {
        List<OrderItem> items = record.items().stream()
                .map(i -> new OrderItem(i.productName(), i.quantity(), i.price()))
                .toList();
        return Order.rehydrate(
                record.id(),
                record.createdAt(),
                record.idempotencyKey(),
                items,
                record.status(),
                record.version());
    }

    /**
     * Projects a domain aggregate into persistence shape.
     *
     * @param domain domain aggregate
     * @return entity ready for repository persistence
     */
    public OrderRecord toRecord(Order domain, String regionId, Instant lastUpdatedTimestamp) {
        return new OrderRecord(
                domain.getId(),
                domain.getVersion(),
                domain.getStatus(),
                domain.getCreatedAt(),
                domain.getIdempotencyKey(),
                regionId,
                lastUpdatedTimestamp,
                toItemRecords(domain.getItems()));
    }

    /**
     * Converts domain aggregate into HTTP response DTO.
     *
     * @param domain domain aggregate
     * @return API-facing response model
     */
    public OrderResponse toResponse(Order domain) {
        List<OrderItemRequest> items = domain.getItems().stream()
                .map(i -> new OrderItemRequest(i.productName(), i.quantity(), i.price()))
                .toList();
        return new OrderResponse(domain.getId(), domain.getStatus(), domain.getCreatedAt(), items);
    }

    private List<OrderItemEmbeddable> toEmbeddables(List<OrderItem> items) {
        return items.stream().map(item -> {
            OrderItemEmbeddable embeddable = new OrderItemEmbeddable();
            embeddable.setProductName(item.productName());
            embeddable.setQuantity(item.quantity());
            embeddable.setPrice(item.price());
            return embeddable;
        }).toList();
    }

    /**
     * Converts persistence-neutral item records back to embeddable persistence items.
     *
     * @param items application-layer item records
     * @return persistence embeddables
     */
    public List<OrderItemEmbeddable> toEmbeddablesFromRecord(List<OrderItemRecord> items) {
        return items.stream().map(item -> {
            OrderItemEmbeddable embeddable = new OrderItemEmbeddable();
            embeddable.setProductName(item.productName());
            embeddable.setQuantity(item.quantity());
            embeddable.setPrice(item.price());
            return embeddable;
        }).toList();
    }

    private List<OrderItemRecord> toItemRecords(List<OrderItem> items) {
        return items.stream()
                .map(item -> new OrderItemRecord(item.productName(), item.quantity(), item.price()))
                .toList();
    }
}
