package com.solar.Quotation.service;
import com.solar.Quotation.dto.responsedto.DashboardSummaryResponseDto;

public interface DashboardService {

    /**
     * Dashboard numbers for the caller. Partner roles always get their own partner (and, for data scope OWN,
     * only their own records). partnerId is honoured for Super Admin only; null means every partner.
     */
    DashboardSummaryResponseDto summary(Long partnerId);
}
