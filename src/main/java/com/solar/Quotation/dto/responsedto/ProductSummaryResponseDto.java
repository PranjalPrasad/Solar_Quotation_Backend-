package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class ProductSummaryResponseDto {

    private long total;
    private long active;
    private long lowStock;
    private long outOfStock;
    private BigDecimal inventoryValue = BigDecimal.ZERO;

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getActive() { return active; }
    public void setActive(long active) { this.active = active; }
    public long getLowStock() { return lowStock; }
    public void setLowStock(long lowStock) { this.lowStock = lowStock; }
    public long getOutOfStock() { return outOfStock; }
    public void setOutOfStock(long outOfStock) { this.outOfStock = outOfStock; }
    public BigDecimal getInventoryValue() { return inventoryValue; }
    public void setInventoryValue(BigDecimal inventoryValue) { this.inventoryValue = inventoryValue; }
}
