package com.example.orderprocessing.application.service;

import com.example.orderprocessing.interfaces.http.dto.OrderItemRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.domain.order.Order;
import com.example.orderprocessing.domain.order.OrderItem;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderItemEmbeddable;
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
     * @param entity JPA entity loaded from storage
     * @return domain aggregate preserving status/version semantics
     */
    public Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.getProductName(), i.getQuantity(), i.getPrice()))
                .toList();
        return Order.rehydrate(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getIdempotencyKey(),
                items,
                entity.getStatus(),
                entity.getVersion());
    }

    /**
     * Projects a domain aggregate into persistence shape.
     *
     * @param domain domain aggregate
     * @return entity ready for repository persistence
     */
    public OrderEntity toEntity(Order domain) {
        OrderEntity entity = new OrderEntity();
        entity.setId(domain.getId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setIdempotencyKey(domain.getIdempotencyKey());
        entity.setStatus(domain.getStatus());
        entity.setVersion(domain.getVersion());
        entity.setItems(toEmbeddables(domain.getItems()));
        return entity;
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
}
