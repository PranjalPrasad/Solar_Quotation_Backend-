package com.solar.Quotation.service;

import com.solar.Quotation.dto.responsedto.AdminDashboardResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardRecentQuotationResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardSummaryResponseDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Tenant rules: summary / recent / charts are READS of ONE workspace: the caller's own tenant, or, for data scope ALL
 * on a GET, the partner named by ?viewPartnerId= (TenantResolver.effectiveReadPartnerId). Data scope OWN is applied.
 * Cross-partner totals exist ONLY in adminSummary(), which is Super Admin only.
 */
public interface DashboardService {

    DashboardSummaryResponseDto summary();

    List<DashboardRecentQuotationResponseDto> recent(int limit);

    /** Legacy chart data (status, size, monthly trend) of the same workspace. partnerIdIgnored is logged and never used. */
    DashboardResponseDto charts(Long partnerIdIgnored, LocalDate from, LocalDate to);

    /** Super Admin only. */
    AdminDashboardResponseDto adminSummary();
}