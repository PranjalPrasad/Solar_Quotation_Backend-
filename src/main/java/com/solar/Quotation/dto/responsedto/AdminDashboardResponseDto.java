package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/admin/dashboard (Super Admin only): the ONLY place where totals across all partners are returned.
 * Nothing here is filtered by a workspace or by ?viewPartnerId=.
 */
public class AdminDashboardResponseDto {

    /** Real partners only (type PARTNER); the PLATFORM tenant is not counted. */
    private long totalPartners;
    private long activePartners;
    /** Quotations and their value over every tenant, the PLATFORM tenant included. */
    private long platformQuotations;
    private BigDecimal platformRevenue = BigDecimal.ZERO;
    /** Referral commission that is approved but not yet paid out, over every tenant. */
    private BigDecimal platformPendingReferralPayouts = BigDecimal.ZERO;
    private List<DashboardTopPartnerResponseDto> topPartners = new ArrayList<>();

    public long getTotalPartners() { return totalPartners; }
    public void setTotalPartners(long v) { this.totalPartners = v; }
    public long getActivePartners() { return activePartners; }
    public void setActivePartners(long v) { this.activePartners = v; }
    public long getPlatformQuotations() { return platformQuotations; }
    public void setPlatformQuotations(long v) { this.platformQuotations = v; }
    public BigDecimal getPlatformRevenue() { return platformRevenue; }
    public void setPlatformRevenue(BigDecimal v) { this.platformRevenue = v; }
    public BigDecimal getPlatformPendingReferralPayouts() { return platformPendingReferralPayouts; }
    public void setPlatformPendingReferralPayouts(BigDecimal v) { this.platformPendingReferralPayouts = v; }
    public List<DashboardTopPartnerResponseDto> getTopPartners() { return topPartners; }
    public void setTopPartners(List<DashboardTopPartnerResponseDto> v) { this.topPartners = v; }
}