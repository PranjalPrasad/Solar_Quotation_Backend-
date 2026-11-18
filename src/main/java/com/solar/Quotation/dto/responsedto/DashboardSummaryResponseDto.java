package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

/**
 * GET /api/dashboard/summary: the KPI cards, status donut, size chart and win rate of ONE workspace (the caller's
 * own tenant, or the partner a Super Admin is viewing). It deliberately has NO platform-wide field: cross-partner
 * totals are in AdminDashboardResponseDto (GET /api/admin/dashboard, Super Admin only).
 */
public class DashboardSummaryResponseDto {

    /** Which workspace these numbers belong to ("My workspace" or a partner name). */
    private DashboardScopeResponseDto scope;

    private long totalCustomers;
    private long totalProjects;
    private long totalQuotations;
    private BigDecimal totalQuotationValue = BigDecimal.ZERO;
    /** Quotations still waiting for a decision (status PENDING). */
    private long pendingApprovals;
    /**
     * Commission earned as the REFERRER (referral status APPROVED + PAID). Null when the caller's data scope is OWN
     * (the commission belongs to the partner, not to one user) so it is never shown as that user's number.
     */
    private BigDecimal totalReferralIncome;

    // ---- status cards, donut chart, win rate (other statuses count only in the total) ----
    private long pendingQuotations;
    private long acceptedQuotations;
    private long rejectedQuotations;
    /** accepted / total, rounded to a whole percent. 0 when there are no quotations. */
    private int winRatePercent;

    // ---- today / this month (Asia/Kolkata calendar, by quotation date) ----
    private long todayQuotations;
    private long monthQuotations;
    private BigDecimal monthQuotationValue = BigDecimal.ZERO;

    // ---- system size bar chart: small <= 5 kW, medium > 5 and <= 20 kW, large > 20 kW ----
    private long smallSystems;
    private long mediumSystems;
    private long largeSystems;

    public DashboardScopeResponseDto getScope() { return scope; }
    public void setScope(DashboardScopeResponseDto scope) { this.scope = scope; }
    public long getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(long v) { this.totalCustomers = v; }
    public long getTotalProjects() { return totalProjects; }
    public void setTotalProjects(long v) { this.totalProjects = v; }
    public long getTotalQuotations() { return totalQuotations; }
    public void setTotalQuotations(long v) { this.totalQuotations = v; }
    public BigDecimal getTotalQuotationValue() { return totalQuotationValue; }
    public void setTotalQuotationValue(BigDecimal v) { this.totalQuotationValue = v; }
    public long getPendingApprovals() { return pendingApprovals; }
    public void setPendingApprovals(long v) { this.pendingApprovals = v; }
    public BigDecimal getTotalReferralIncome() { return totalReferralIncome; }
    public void setTotalReferralIncome(BigDecimal v) { this.totalReferralIncome = v; }
    public long getPendingQuotations() { return pendingQuotations; }
    public void setPendingQuotations(long v) { this.pendingQuotations = v; }
    public long getAcceptedQuotations() { return acceptedQuotations; }
    public void setAcceptedQuotations(long v) { this.acceptedQuotations = v; }
    public long getRejectedQuotations() { return rejectedQuotations; }
    public void setRejectedQuotations(long v) { this.rejectedQuotations = v; }
    public int getWinRatePercent() { return winRatePercent; }
    public void setWinRatePercent(int v) { this.winRatePercent = v; }
    public long getTodayQuotations() { return todayQuotations; }
    public void setTodayQuotations(long v) { this.todayQuotations = v; }
    public long getMonthQuotations() { return monthQuotations; }
    public void setMonthQuotations(long v) { this.monthQuotations = v; }
    public BigDecimal getMonthQuotationValue() { return monthQuotationValue; }
    public void setMonthQuotationValue(BigDecimal v) { this.monthQuotationValue = v; }
    public long getSmallSystems() { return smallSystems; }
    public void setSmallSystems(long v) { this.smallSystems = v; }
    public long getMediumSystems() { return mediumSystems; }
    public void setMediumSystems(long v) { this.mediumSystems = v; }
    public long getLargeSystems() { return largeSystems; }
    public void setLargeSystems(long v) { this.largeSystems = v; }
}