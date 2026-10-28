package com.solar.Quotation.controller;
import com.solar.Quotation.dto.requestdto.QuotationHistoryDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.QuotationHistoryService;
import com.solar.Quotation.service.QuotationAccessGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quotations")
public class QuotationHistoryController {

    private final QuotationHistoryService history;
    private final QuotationAccessGuard access;

    public QuotationHistoryController(QuotationHistoryService history, QuotationAccessGuard access) {
        this.history = history;
        this.access = access;
    }

    /** Newest first. Same visibility rules as GET /api/quotations/{id}. */
    @GetMapping("/{id}/history")
    @RequirePermission("QUOTATION:VIEW")
    public ApiResponse<List<QuotationHistoryDto>> history(@PathVariable Long id) {
        access.assertCanView(id);
        return ApiResponse.ok(history.list(id));
    }
}
