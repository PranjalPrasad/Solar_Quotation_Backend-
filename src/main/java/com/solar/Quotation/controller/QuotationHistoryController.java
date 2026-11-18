package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.QuotationHistoryDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.QuotationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quotations")
public class QuotationHistoryController {

    private final QuotationService quotations;

    public QuotationHistoryController(QuotationService quotations) {
        this.quotations = quotations;
    }

    /**
     * Newest first. The quotation is tenant-checked first (own tenant, or ?viewPartnerId= for Super Admin), then the
     * history is read with that quotation's partner id. Another partner's id is a 404, not an empty list.
     */
    @GetMapping("/{id}/history")
    @RequirePermission("QUOTATION:VIEW")
    public ApiResponse<List<QuotationHistoryDto>> history(@PathVariable Long id) {
        return ApiResponse.ok(quotations.history(id));
    }
}