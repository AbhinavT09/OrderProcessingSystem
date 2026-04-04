package com.example.orderprocessing.infrastructure.persistence.entity;

import jakarta.persistence.Embeddable;

@Embeddable
/**
 * OrderItemEmbeddable implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OrderItemEmbeddable {

    private String productName;
    private Integer quantity;
    private Double price;

    /**
     * Returns productName value.
     * @return operation result
     */
    public String getProductName() {
        return productName;
    }

    /**
     * Sets productName value.
     * @param productName input argument used by this operation
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    /**
     * Returns quantity value.
     * @return operation result
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * Sets quantity value.
     * @param quantity input argument used by this operation
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * Returns price value.
     * @return operation result
     */
    public Double getPrice() {
        return price;
    }

    /**
     * Sets price value.
     * @param price input argument used by this operation
     */
    public void setPrice(Double price) {
        this.price = price;
    }
}
