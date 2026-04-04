package com.example.orderprocessing.application.service;

import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import java.util.List;

/**
 * CachedOrderList record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record CachedOrderList(List<OrderResponse> orders) {
}
