package com.example.orderprocessing.interfaces.http.controller;

import com.example.orderprocessing.interfaces.http.dto.CreateOrderRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.interfaces.http.dto.UpdateOrderStatusRequest;
import com.example.orderprocessing.application.service.OrderQueryService;
import com.example.orderprocessing.application.service.OrderService;
import com.example.orderprocessing.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping("/orders")
/**
 * OrderController implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    /**
     * Creates a controller with command and query services.
     * @param orderService service handling command-side operations
     * @param orderQueryService service handling query-side operations
     */
    public OrderController(OrderService orderService, OrderQueryService orderQueryService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /**
     * Creates a new order using an optional idempotency key.
     * @param request validated create-order payload
     * @param idempotencyKey optional request idempotency key
     * @return created order view
     */
    public OrderResponse create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = "X-Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        return orderService.createOrder(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    /**
     * Returns byId value.
     * @param id input argument used by this operation
     * @return operation result
     */
    public OrderResponse getById(@PathVariable UUID id) {
        return orderQueryService.getById(id);
    }

    @PatchMapping("/{id}/status")
    /**
     * Executes updateStatus.
     * @param id input argument used by this operation
     * @param request input argument used by this operation
     * @return operation result
     */
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.version());
    }

    @GetMapping
    /**
     * Lists orders, optionally filtered by status.
     * @param status optional order status filter
     * @return ordered list of matching orders
     */
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return orderQueryService.list(status);
    }

    @PatchMapping("/{id}/cancel")
    /**
     * Executes cancel.
     * @param id input argument used by this operation
     * @return operation result
     */
    public OrderResponse cancel(@PathVariable UUID id) {
        return orderService.cancel(id);
    }
}
