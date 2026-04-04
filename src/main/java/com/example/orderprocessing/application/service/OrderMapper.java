package com.example.orderprocessing.application.service;

import com.example.orderprocessing.api.dto.OrderItemRequest;
import com.example.orderprocessing.api.dto.OrderResponse;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderItemEmbeddable;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public List<OrderItemEmbeddable> toEmbeddables(List<OrderItemRequest> items) {
        return items.stream().map(item -> {
            OrderItemEmbeddable embeddable = new OrderItemEmbeddable();
            embeddable.setProductName(item.productName());
            embeddable.setQuantity(item.quantity());
            embeddable.setPrice(item.price());
            return embeddable;
        }).toList();
    }

    public OrderResponse toResponse(OrderEntity order) {
        List<OrderItemRequest> items = order.getItems().stream()
                .map(i -> new OrderItemRequest(i.getProductName(), i.getQuantity(), i.getPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), order.getCreatedAt(), items);
    }
}
