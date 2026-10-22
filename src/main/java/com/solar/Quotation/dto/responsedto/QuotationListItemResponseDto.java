package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A row of the quotation table. No items and no savings, so a page of 100 stays light. */
public class QuotationListItemResponseDto {

    private Long id;
    private String code;
    private CustomerRefResponseDto customer;
    private BigDecimal systemSizeKw;
    private BigDecimal totalAmount;
    private BigDecimal balanceAmount;
    private String status;
    private LocalDate quoteDate;
    private LocalDate validUntil;
    private boolean referred;
    private String referredBy;
    private PartnerRefResponseDto partner;
    private AuditInfoResponseDto audit;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public CustomerRefResponseDto getCustomer() { return customer; }
    public void setCustomer(CustomerRefResponseDto customer) { this.customer = customer; }
    public BigDecimal getSystemSizeKw() { return systemSizeKw; }
    public void setSystemSizeKw(BigDecimal systemSizeKw) { this.systemSizeKw = systemSizeKw; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public boolean isReferred() { return referred; }
    public void setReferred(boolean referred) { this.referred = referred; }
    public String getReferredBy() { return referredBy; }
    public void setReferredBy(String referredBy) { this.referredBy = referredBy; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public AuditInfoResponseDto getAudit() { return audit; }
    public void setAudit(AuditInfoResponseDto audit) { this.audit = audit; }
}
