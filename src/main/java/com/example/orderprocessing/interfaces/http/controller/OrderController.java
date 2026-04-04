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
 * HTTP interface adapter for order commands and queries.
 *
 * <p>Belongs to the interface layer and delegates all business decisions to application services.
 * Keeps transport concerns (validation, headers, path/query binding) separate from domain logic.</p>
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
     * Accepts order creation command with optional idempotency header.
     *
     * <p>When the header is supplied, duplicate client retries can be collapsed into a single
     * committed order outcome by the application-layer idempotency workflow.</p>
     *
     * @param request validated order payload
     * @param idempotencyKey optional retry-correlation key from client
     * @return created order, or previously completed order for same key
     */
    public OrderResponse create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = "X-Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        return orderService.createOrder(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    /**
     * Retrieves a single order view by identifier.
     *
     * @param id order identifier
     * @return materialized order response
     */
    public OrderResponse getById(@PathVariable UUID id) {
        return orderQueryService.getById(id);
    }

    @PatchMapping("/{id}/status")
    /**
     * Applies status transition command with optimistic concurrency version.
     *
     * @param id order identifier
     * @param request target status and expected version
     * @return updated order response
     */
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.version());
    }

    @GetMapping
    /**
     * Lists orders, optionally filtered by domain status.
     *
     * @param status optional status filter
     * @return order responses matching filter criteria
     */
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return orderQueryService.list(status);
    }

    @PatchMapping("/{id}/cancel")
    /**
     * Requests cancellation of an order.
     *
     * @param id order identifier
     * @return cancelled order response when domain rules allow it
     */
    public OrderResponse cancel(@PathVariable UUID id) {
        return orderService.cancel(id);
    }
}
