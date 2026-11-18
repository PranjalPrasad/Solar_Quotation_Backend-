package com.solar.Quotation.controller;

import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.DashboardRecentQuotationResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardResponseDto;
import com.solar.Quotation.dto.responsedto.DashboardSummaryResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * One workspace per call: the caller's own tenant, or (Super Admin, GET only) the partner in ?viewPartnerId=.
 * The widgets are separate endpoints so one slow or failing query only affects its own widget.
 * Cross-partner totals: GET /api/admin/dashboard (AdminDashboardController).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** KPI cards, status donut, size chart, win rate, and the scope label. */
    @GetMapping("/summary")
    @RequirePermission("DASHBOARD:VIEW")
    public ApiResponse<DashboardSummaryResponseDto> summary() {
        return ApiResponse.ok(service.summary());
    }

    /** Latest quotations (right panel). */
    @GetMapping("/recent")
    @RequirePermission("DASHBOARD:VIEW")
    public ApiResponse<List<DashboardRecentQuotationResponseDto>> recent(@RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(service.recent(limit));
    }

    /** Status counts, size distribution and monthly trend. partnerId is ignored (logged at WARN). */
    @GetMapping("/charts")
    @RequirePermission("DASHBOARD:VIEW")
    public ApiResponse<DashboardResponseDto> charts(
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.charts(partnerId, from, to));
    }
}