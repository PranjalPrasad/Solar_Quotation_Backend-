package com.solar.Quotation.controller;

import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.DashboardSummaryResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** ?partnerId= is honoured for Super Admin only (TenantResolver decides); partner roles always get their own data. */
    @GetMapping("/summary")
    @RequirePermission("DASHBOARD:VIEW")
    public ApiResponse<DashboardSummaryResponseDto> summary(@RequestParam(required = false) Long partnerId) {
        return ApiResponse.ok(service.summary(partnerId));
    }
}
