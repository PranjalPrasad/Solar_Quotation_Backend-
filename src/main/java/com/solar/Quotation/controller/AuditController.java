package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.AuditFilterRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.AuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Super Admin only (AUDIT is a platform-only module). */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("AUDIT:VIEW")
    public ApiResponse<PageResponse<AuditLogResponseDto>> list(AuditFilterRequestDto filter) {
        return ApiResponse.ok(service.search(filter));
    }

    /** Dropdown values for the screen's filters, so the page has no hard-coded lists. */
    @GetMapping("/filters")
    @RequirePermission("AUDIT:VIEW")
    public ApiResponse<java.util.Map<String, java.util.List<String>>> filters() {
        return ApiResponse.ok(service.filterOptions());
    }

    @GetMapping("/export")
    @RequirePermission("AUDIT:EXPORT")
    public ResponseEntity<byte[]> export(AuditFilterRequestDto filter) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(service.exportCsv(filter));
    }
}