package com.example.orderprocessing.domain.model;

/**
 * OrderItem record captures immutable data transferred between layers.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public record OrderItem(String productName, Integer quantity, Double price) {
}
