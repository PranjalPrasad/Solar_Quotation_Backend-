package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class ReferralSummaryResponseDto {

    private long totalReferrals;
    private long pendingCount;
    private long approvedCount;
    private long paidCount;
    private long rejectedCount;
    private BigDecimal pendingAmount = BigDecimal.ZERO;
    private BigDecimal pendingPayout = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal totalEarned = BigDecimal.ZERO;
    private BigDecimal paidThisMonth = BigDecimal.ZERO;

    public long getTotalReferrals() { return totalReferrals; }
    public void setTotalReferrals(long totalReferrals) { this.totalReferrals = totalReferrals; }
    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
    public long getApprovedCount() { return approvedCount; }
    public void setApprovedCount(long approvedCount) { this.approvedCount = approvedCount; }
    public long getPaidCount() { return paidCount; }
    public void setPaidCount(long paidCount) { this.paidCount = paidCount; }
    public long getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(long rejectedCount) { this.rejectedCount = rejectedCount; }
    public BigDecimal getPendingAmount() { return pendingAmount; }
    public void setPendingAmount(BigDecimal pendingAmount) { this.pendingAmount = pendingAmount; }
    public BigDecimal getPendingPayout() { return pendingPayout; }
    public void setPendingPayout(BigDecimal pendingPayout) { this.pendingPayout = pendingPayout; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getTotalEarned() { return totalEarned; }
    public void setTotalEarned(BigDecimal totalEarned) { this.totalEarned = totalEarned; }
    public BigDecimal getPaidThisMonth() { return paidThisMonth; }
    public void setPaidThisMonth(BigDecimal paidThisMonth) { this.paidThisMonth = paidThisMonth; }
}
