package com.example.orderprocessing.interfaces.http.dto;

import com.example.orderprocessing.domain.order.OrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * HTTP command payload for order status updates.
 *
 * <p>Includes target status and expected version to support optimistic-concurrency semantics.</p>
 */
public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        @NotNull @Min(0) Long version
) {
}
