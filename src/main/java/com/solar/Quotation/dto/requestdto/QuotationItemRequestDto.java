package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One BOQ line. productId is null for a custom item. The amount is never sent: the server computes qty x rate. */
public class QuotationItemRequestDto {

    private Long productId;

    @NotBlank(message = "Item name is required")
    @Size(max = 200, message = "Item name is too long")
    private String name;

    @Size(max = 200, message = "Item type is too long")
    private String type;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be more than 0")
    @DecimalMax(value = "9999999", message = "Quantity is too large")
    private BigDecimal qty;

    @NotBlank(message = "Unit is required")
    @Size(max = 20, message = "Unit is too long")
    private String unit;

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0", message = "Rate cannot be negative")
    @DecimalMax(value = "999999999", message = "Rate is too large")
    private BigDecimal rate;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
}
