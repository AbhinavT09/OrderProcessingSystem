package com.example.orderprocessing.infrastructure.persistence.jpa;

import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderItemEmbeddable;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOrderJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JPA slice: Spring Data queries and Hibernate mapping on embedded H2 (no servlet stack).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SpringOrderJpaRepositoryDataJpaTest {

    @Autowired
    private SpringOrderJpaRepository repository;

    @Test
    void findByStatusOrderByCreatedAtAsc_returnsOldestFirstPage() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-02T00:00:00Z");
        repository.save(order(OrderStatus.PENDING, t0, "older"));
        repository.save(order(OrderStatus.PENDING, t1, "newer"));

        Page<OrderEntity> page = repository.findByStatusOrderByCreatedAtAsc(
                OrderStatus.PENDING, PageRequest.of(0, 1));

        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals("older", page.getContent().get(0).getItems().get(0).getProductName());
    }

    @Test
    void findByOwnerSubjectAndStatus_scopesToOwner() {
        OrderEntity a = orderForOwner("u1", OrderStatus.PENDING);
        OrderEntity b = orderForOwner("u2", OrderStatus.PENDING);
        repository.save(a);
        repository.save(b);

        Page<OrderEntity> u1only = repository.findByOwnerSubjectAndStatus(
                "u1", OrderStatus.PENDING, PageRequest.of(0, 10));

        assertEquals(1, u1only.getTotalElements());
        assertEquals(a.getId(), u1only.getContent().get(0).getId());
    }

    private static OrderEntity order(OrderStatus status, Instant createdAt, String product) {
        OrderEntity e = new OrderEntity();
        e.setId(UUID.randomUUID());
        e.setVersion(0L);
        e.setStatus(status);
        e.setCreatedAt(createdAt);
        e.setRegionId("region-a");
        e.setLastUpdatedTimestamp(createdAt);
        e.setOwnerSubject("owner");
        e.setItems(List.of(line(product, 1, 1.0)));
        return e;
    }

    private static OrderEntity orderForOwner(String owner, OrderStatus status) {
        Instant now = Instant.now();
        OrderEntity e = new OrderEntity();
        e.setId(UUID.randomUUID());
        e.setVersion(0L);
        e.setStatus(status);
        e.setCreatedAt(now);
        e.setRegionId("region-a");
        e.setLastUpdatedTimestamp(now);
        e.setOwnerSubject(owner);
        e.setItems(List.of(line("p", 1, 1.0)));
        return e;
    }

    private static OrderItemEmbeddable line(String product, int qty, double price) {
        OrderItemEmbeddable i = new OrderItemEmbeddable();
        i.setProductName(product);
        i.setQuantity(qty);
        i.setPrice(price);
        return i;
    }
}
