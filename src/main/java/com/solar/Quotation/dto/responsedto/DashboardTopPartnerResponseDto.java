package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class DashboardTopPartnerResponseDto {

    private Long partnerId;
    private String partnerName;
    private String partnerCode;
    private long totalQuotations;
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }
    public long getTotalQuotations() { return totalQuotations; }
    public void setTotalQuotations(long totalQuotations) { this.totalQuotations = totalQuotations; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
}
