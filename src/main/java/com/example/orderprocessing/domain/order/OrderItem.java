package com.example.orderprocessing.domain.order;

/**
 * Domain value object representing one order line item.
 *
 * <p>Belongs to the domain layer and models purchased product attributes independent of
 * transport or persistence concerns.</p>
 */
public record OrderItem(String productName, Integer quantity, Double price) {
}
