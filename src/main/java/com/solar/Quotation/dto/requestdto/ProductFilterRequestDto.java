package com.solar.Quotation.dto.requestdto;
import java.math.BigDecimal;

/** stockStatus: IN_STOCK, LOW_STOCK or OUT_OF_STOCK. */
public class ProductFilterRequestDto extends PageFilterRequestDto {

    private String category;
    private String status;
    private BigDecimal gstPercent;
    private String stockStatus;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
}
