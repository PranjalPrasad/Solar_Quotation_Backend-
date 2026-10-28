package com.solar.Quotation.dto.responsedto;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A row of the dashboard "Recent Quotations" table / right panel. Light on purpose: no items, no savings. */
public class DashboardRecentQuotationResponseDto {

    private String code;
    private String customerName;
    /** Filled for Super Admin only (the table shows the Channel Partner column only for them). */
    private String partnerCode;
    private BigDecimal systemSizeKw;
    private BigDecimal totalAmount;
    /** PENDING / ACCEPTED / REJECTED. */
    private String status;
    private LocalDate quoteDate;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }
    public BigDecimal getSystemSizeKw() { return systemSizeKw; }
    public void setSystemSizeKw(BigDecimal systemSizeKw) { this.systemSizeKw = systemSizeKw; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }
}
