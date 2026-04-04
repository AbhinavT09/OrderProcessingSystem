package com.example.orderprocessing.application.service;

import com.example.orderprocessing.api.dto.OrderItemRequest;
import com.example.orderprocessing.api.dto.OrderResponse;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderItemEmbeddable;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
/**
 * OrderMapper implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OrderMapper {

    /**
     * Executes toDomainItems.
     * @param items input argument used by this operation
     * @return operation result
     */
    public List<OrderItem> toDomainItems(List<OrderItemRequest> items) {
        return items.stream().map(item -> new OrderItem(item.productName(), item.quantity(), item.price())).toList();
    }

    /**
     * Executes toDomain.
     * @param entity input argument used by this operation
     * @return operation result
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
     * Executes toEntity.
     * @param domain input argument used by this operation
     * @return operation result
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
     * Executes toResponse.
     * @param domain input argument used by this operation
     * @return operation result
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
