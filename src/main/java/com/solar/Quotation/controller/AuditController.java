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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform staff only (AUDIT is a platform-only module). The real gate is AuditAccessGuard inside AuditService:
 * 403 for every partner user and for platform staff without AUDIT:VIEW (AUDIT:EXPORT for the export).
 * The annotations below document the permission; they are not what enforces it.
 *
 * Filters (all optional, combinable): partnerId | partner=PLATFORM, userId, module, action, entityType, entityId,
 * from, to (UTC dates, inclusive), search (user name / record name / record type); page, size, sort.
 */
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

    /** One entry including the masked previous_data / new_data, for the details drawer. */
    @GetMapping("/{id:\\d+}")
    @RequirePermission("AUDIT:VIEW")
    public ApiResponse<AuditLogResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
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