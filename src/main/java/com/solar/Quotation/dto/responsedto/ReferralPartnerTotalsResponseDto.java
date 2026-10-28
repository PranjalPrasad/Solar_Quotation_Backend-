package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class ReferralPartnerTotalsResponseDto {

    private Long partnerId;
    private long totalReferrals;
    private BigDecimal totalEarned = BigDecimal.ZERO;
    private BigDecimal pendingPayout = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;

    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public long getTotalReferrals() { return totalReferrals; }
    public void setTotalReferrals(long totalReferrals) { this.totalReferrals = totalReferrals; }
    public BigDecimal getTotalEarned() { return totalEarned; }
    public void setTotalEarned(BigDecimal totalEarned) { this.totalEarned = totalEarned; }
    public BigDecimal getPendingPayout() { return pendingPayout; }
    public void setPendingPayout(BigDecimal pendingPayout) { this.pendingPayout = pendingPayout; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
}