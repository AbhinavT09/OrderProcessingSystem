package com.example.orderprocessing.application.service;

import com.example.orderprocessing.application.port.OrderItemRecord;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.interfaces.http.dto.OrderItemRequest;
import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import com.example.orderprocessing.domain.order.Order;
import com.example.orderprocessing.domain.order.OrderItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    @Test
    void toDomainItems_mapsAllFields() {
        List<OrderItem> items = mapper.toDomainItems(List.of(
                new OrderItemRequest("A", 2, 3.5),
                new OrderItemRequest("B", 1, 0.0)));
        assertEquals(2, items.size());
        assertEquals("A", items.get(0).productName());
        assertEquals(2, items.get(0).quantity());
        assertEquals(3.5, items.get(0).price());
    }

    @Test
    void toDomain_roundTripsRecord() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T12:00:00Z");
        OrderRecord record = new OrderRecord(
                id,
                3L,
                OrderStatus.PROCESSING,
                created,
                "idem-1",
                "owner-x",
                "region-a",
                Instant.parse("2026-01-02T12:00:00Z"),
                List.of(new OrderItemRecord("P", 1, 10.0)));
        Order order = mapper.toDomain(record);
        assertEquals(id, order.getId());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertEquals("owner-x", order.getOwnerSubject());
        assertEquals(3L, order.getVersion());
        assertEquals(1, order.getItems().size());
    }

    @Test
    void toResponse_mapsDomainToDto() {
        Order order = Order.rehydrate(
                UUID.randomUUID(),
                Instant.now(),
                "k",
                "sub",
                List.of(new OrderItem("X", 1, 2.0)),
                OrderStatus.PENDING,
                0L);
        OrderResponse response = mapper.toResponse(order);
        assertEquals(order.getId(), response.id());
        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(1, response.items().size());
        assertEquals("X", response.items().get(0).productName());
    }

    @Test
    void toRecord_includesRegionAndItems() {
        Order order = Order.create(List.of(new OrderItem("Y", 2, 4.0)), null, "u1");
        Instant ts = Instant.parse("2026-03-01T00:00:00Z");
        OrderRecord record = mapper.toRecord(order, "r-west", ts);
        assertEquals("r-west", record.regionId());
        assertEquals(ts, record.lastUpdatedTimestamp());
        assertEquals(1, record.items().size());
        assertEquals("Y", record.items().get(0).productName());
    }

    @Test
    void toEmbeddablesFromRecord_mapsItemRecords() {
        var embeddables = mapper.toEmbeddablesFromRecord(List.of(new OrderItemRecord("Z", 3, 1.5)));
        assertEquals(1, embeddables.size());
        assertEquals("Z", embeddables.get(0).getProductName());
        assertEquals(3, embeddables.get(0).getQuantity());
        assertEquals(1.5, embeddables.get(0).getPrice());
    }
}
