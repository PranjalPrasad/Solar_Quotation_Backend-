package com.solar.Quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One stock IN / OUT line in a product's ledger. Written by ProductService.adjustStock. */
@Entity
@Table(name = "product_stock_movement",
        indexes = @Index(name = "idx_psm_product_created", columnList = "product_id,created_at"))
public class ProductStockMovement extends TenantBaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    /** IN or OUT. */
    @Column(nullable = false, length = 3, updatable = false)
    private String type;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(length = 200, updatable = false)
    private String reason;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(int balanceAfter) { this.balanceAfter = balanceAfter; }
}
