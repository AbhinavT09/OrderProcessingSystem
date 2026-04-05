package com.example.orderprocessing.application.port;

/**
 * Persistence-neutral order item snapshot used by application ports.
 *
 * @param productName product label
 * @param quantity ordered quantity
 * @param price unit price
 */
public record OrderItemRecord(
        String productName,
        int quantity,
        double price) {
}
