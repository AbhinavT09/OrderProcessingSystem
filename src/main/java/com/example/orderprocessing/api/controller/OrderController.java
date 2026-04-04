package com.example.orderprocessing.api.controller;

import com.example.orderprocessing.api.dto.CreateOrderRequest;
import com.example.orderprocessing.api.dto.OrderResponse;
import com.example.orderprocessing.api.dto.UpdateOrderStatusRequest;
import com.example.orderprocessing.application.service.OrderApplicationService;
import com.example.orderprocessing.domain.model.OrderStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService service;

    public OrderController(OrderApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.createOrder(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return service.list(status);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
