package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashboardSummaryResponseDto {

    // ---- already read by dashboard.js ----
    private long totalCustomers;
    private long totalProjects;
    private long totalQuotations;
    private BigDecimal totalQuotationValue = BigDecimal.ZERO;
    /** Quotations still waiting for a decision (status PENDING). */
    private long pendingApprovals;
    /** Channel Partner only: referral commission earned as the REFERRER (approved + paid). Null for Super Admin. */
    private BigDecimal totalReferralIncome;

    // ---- Super Admin only ----
    private Long totalPartners;
    private Long activePartners;
    /** Referral commission that is approved but not yet paid out, platform wide. */
    private BigDecimal platformPendingReferralPayouts;
    private List<DashboardTopPartnerResponseDto> topPartners;

    // ---- status cards, donut chart, win rate ----
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

    // ---- latest quotations (newest first) ----
    private List<DashboardRecentQuotationResponseDto> recentQuotations = new ArrayList<>();

    public long getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(long totalCustomers) { this.totalCustomers = totalCustomers; }
    public long getTotalProjects() { return totalProjects; }
    public void setTotalProjects(long totalProjects) { this.totalProjects = totalProjects; }
    public long getTotalQuotations() { return totalQuotations; }
    public void setTotalQuotations(long totalQuotations) { this.totalQuotations = totalQuotations; }
    public BigDecimal getTotalQuotationValue() { return totalQuotationValue; }
    public void setTotalQuotationValue(BigDecimal totalQuotationValue) { this.totalQuotationValue = totalQuotationValue; }
    public long getPendingApprovals() { return pendingApprovals; }
    public void setPendingApprovals(long pendingApprovals) { this.pendingApprovals = pendingApprovals; }
    public BigDecimal getTotalReferralIncome() { return totalReferralIncome; }
    public void setTotalReferralIncome(BigDecimal totalReferralIncome) { this.totalReferralIncome = totalReferralIncome; }
    public Long getTotalPartners() { return totalPartners; }
    public void setTotalPartners(Long totalPartners) { this.totalPartners = totalPartners; }
    public Long getActivePartners() { return activePartners; }
    public void setActivePartners(Long activePartners) { this.activePartners = activePartners; }
    public BigDecimal getPlatformPendingReferralPayouts() { return platformPendingReferralPayouts; }
    public void setPlatformPendingReferralPayouts(BigDecimal platformPendingReferralPayouts) { this.platformPendingReferralPayouts = platformPendingReferralPayouts; }
    public List<DashboardTopPartnerResponseDto> getTopPartners() { return topPartners; }
    public void setTopPartners(List<DashboardTopPartnerResponseDto> topPartners) { this.topPartners = topPartners; }
    public long getPendingQuotations() { return pendingQuotations; }
    public void setPendingQuotations(long pendingQuotations) { this.pendingQuotations = pendingQuotations; }
    public long getAcceptedQuotations() { return acceptedQuotations; }
    public void setAcceptedQuotations(long acceptedQuotations) { this.acceptedQuotations = acceptedQuotations; }
    public long getRejectedQuotations() { return rejectedQuotations; }
    public void setRejectedQuotations(long rejectedQuotations) { this.rejectedQuotations = rejectedQuotations; }
    public int getWinRatePercent() { return winRatePercent; }
    public void setWinRatePercent(int winRatePercent) { this.winRatePercent = winRatePercent; }
    public long getTodayQuotations() { return todayQuotations; }
    public void setTodayQuotations(long todayQuotations) { this.todayQuotations = todayQuotations; }
    public long getMonthQuotations() { return monthQuotations; }
    public void setMonthQuotations(long monthQuotations) { this.monthQuotations = monthQuotations; }
    public BigDecimal getMonthQuotationValue() { return monthQuotationValue; }
    public void setMonthQuotationValue(BigDecimal monthQuotationValue) { this.monthQuotationValue = monthQuotationValue; }
    public long getSmallSystems() { return smallSystems; }
    public void setSmallSystems(long smallSystems) { this.smallSystems = smallSystems; }
    public long getMediumSystems() { return mediumSystems; }
    public void setMediumSystems(long mediumSystems) { this.mediumSystems = mediumSystems; }
    public long getLargeSystems() { return largeSystems; }
    public void setLargeSystems(long largeSystems) { this.largeSystems = largeSystems; }
    public List<DashboardRecentQuotationResponseDto> getRecentQuotations() { return recentQuotations; }
    public void setRecentQuotations(List<DashboardRecentQuotationResponseDto> recentQuotations) { this.recentQuotations = recentQuotations; }
}
