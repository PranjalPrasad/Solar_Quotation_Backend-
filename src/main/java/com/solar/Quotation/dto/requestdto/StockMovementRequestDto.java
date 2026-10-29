package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/products/{id}/stock-history. type is IN or OUT, quantity is always positive. */
public class StockMovementRequestDto {

    @NotNull(message = "Type is required")
    @Pattern(regexp = "IN|OUT", message = "Type must be IN or OUT")
    private String type;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @Size(max = 200, message = "Reason is too long")
    private String reason;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
