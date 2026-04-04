package com.example.orderprocessing.application.service;

import com.example.orderprocessing.api.dto.OrderResponse;
import java.util.List;

public record CachedOrderList(List<OrderResponse> orders) {
}
