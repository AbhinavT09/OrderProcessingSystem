package com.example.orderprocessing.application.service;

import com.example.orderprocessing.interfaces.http.dto.OrderResponse;
import java.util.List;

/**
 * Immutable cache payload for order-list query responses.
 *
 * <p><b>Architecture role:</b> application-layer cache value object used by {@code OrderQueryService}
 * for read-model caching and thundering-herd mitigation.</p>
 *
 * <p><b>Idempotency and resilience context:</b> this payload is safe for repeated reads and
 * re-population. Cache misses or cache failures fall back to repository reads.</p>
 *
 * <p><b>Transaction boundary:</b> read-only query path; no explicit transactional side effects.</p>
 */
public record CachedOrderList(List<OrderResponse> orders) {
}
