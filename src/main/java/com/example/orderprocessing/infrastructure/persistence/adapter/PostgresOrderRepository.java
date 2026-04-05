package com.example.orderprocessing.infrastructure.persistence.adapter;

import com.example.orderprocessing.application.port.OrderItemRecord;
import com.example.orderprocessing.application.port.OrderRecord;
import com.example.orderprocessing.application.port.OrderRepository;
import com.example.orderprocessing.domain.order.OrderStatus;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderEntity;
import com.example.orderprocessing.infrastructure.persistence.entity.OrderItemEmbeddable;
import com.example.orderprocessing.infrastructure.persistence.repository.SpringOrderJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
/**
 * Infrastructure adapter that implements the order repository port via JPA/PostgreSQL.
 *
 * <p>Provides persistence operations used by command and query services while keeping storage
 * concerns isolated from application and domain layers.</p>
 *
 * <p><b>Port/adapter boundary:</b> maps persistence-neutral {@code OrderRecord} objects to
 * JPA {@code OrderEntity} storage models and back.</p>
 *
 * <p><b>Transactional context:</b> invoked inside application service DB transactions.
 * No Kafka transaction logic exists in this adapter.</p>
 */
public class PostgresOrderRepository implements OrderRepository {

    private final SpringOrderJpaRepository repository;

    /**
     * Creates a PostgreSQL order repository adapter.
     * @param repository Spring Data repository dependency
     */
    public PostgresOrderRepository(SpringOrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    /**
     * Persists an order snapshot.
     *
     * @param order entity state to write
     * @return persisted entity
     */
    public OrderRecord save(OrderRecord order) {
        return toRecord(repository.save(toEntity(order)));
    }

    @Override
    /**
     * Loads an order by id.
     *
     * @param id order identifier
     * @return order entity when present
     */
    public Optional<OrderRecord> findById(UUID id) {
        return repository.findById(id).map(this::toRecord);
    }

    @Override
    /**
     * Lists all orders.
     *
     * @return all persisted order entities
     */
    public List<OrderRecord> findAll() {
        return repository.findAll().stream().map(this::toRecord).toList();
    }

    @Override
    /**
     * Lists orders in a specific status ordered by creation time.
     *
     * @param status status filter
     * @return matching order entities
     */
    public List<OrderRecord> findByStatus(OrderStatus status) {
        return repository.findByStatusOrderByCreatedAtAsc(status).stream().map(this::toRecord).toList();
    }

    @Override
    public Page<OrderRecord> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toRecord);
    }

    @Override
    public Page<OrderRecord> findByStatus(OrderStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(this::toRecord);
    }

    @Override
    /**
     * Resolves an order associated with an idempotency key.
     *
     * @param idempotencyKey external request key
     * @return matching order when key mapping exists
     */
    public Optional<OrderRecord> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toRecord);
    }

    private OrderRecord toRecord(OrderEntity entity) {
        List<OrderItemRecord> items = entity.getItems().stream()
                .map(i -> new OrderItemRecord(i.getProductName(), i.getQuantity(), i.getPrice()))
                .toList();
        return new OrderRecord(
                entity.getId(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getIdempotencyKey(),
                entity.getRegionId(),
                entity.getLastUpdatedTimestamp(),
                items);
    }

    private OrderEntity toEntity(OrderRecord record) {
        OrderEntity entity = new OrderEntity();
        entity.setId(record.id());
        entity.setVersion(record.version());
        entity.setStatus(record.status());
        entity.setCreatedAt(record.createdAt());
        entity.setIdempotencyKey(record.idempotencyKey());
        entity.setRegionId(record.regionId());
        entity.setLastUpdatedTimestamp(record.lastUpdatedTimestamp());
        List<OrderItemEmbeddable> items = record.items().stream().map(item -> {
            OrderItemEmbeddable embeddable = new OrderItemEmbeddable();
            embeddable.setProductName(item.productName());
            embeddable.setQuantity(item.quantity());
            embeddable.setPrice(item.price());
            return embeddable;
        }).toList();
        entity.setItems(items);
        return entity;
    }
}
