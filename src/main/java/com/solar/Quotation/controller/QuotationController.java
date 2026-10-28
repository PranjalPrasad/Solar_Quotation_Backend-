package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.QuotationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationStatusRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.QuotationListItemResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationSummaryResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.QuotationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quotations")
public class QuotationController {

    private final QuotationService service;
    public QuotationController(QuotationService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("QUOTATION:VIEW")
    public ApiResponse<PageResponse<QuotationListItemResponseDto>> list(QuotationFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    @GetMapping("/summary")
    @RequirePermission("QUOTATION:VIEW")
    public ApiResponse<QuotationSummaryResponseDto> summary(@RequestParam(required = false) Long partnerId) {
        return ApiResponse.ok(service.summary(partnerId));
    }

    @GetMapping("/{id}")
    @RequirePermission("QUOTATION:VIEW")
    public ApiResponse<QuotationResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }


    @PostMapping("/preview")
    @RequirePermission("QUOTATION:CREATE")
    public ApiResponse<QuotationResponseDto> preview(@Valid @RequestBody QuotationRequestDto req) {
        return ApiResponse.ok(service.preview(req));
    }

    @PostMapping
    @RequirePermission("QUOTATION:CREATE")
    public ResponseEntity<ApiResponse<QuotationResponseDto>> create(@Valid @RequestBody QuotationRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Quotation created"));
    }

    @PutMapping("/{id}")
    @RequirePermission("QUOTATION:EDIT")
    public ApiResponse<QuotationResponseDto> update(@PathVariable Long id, @Valid @RequestBody QuotationRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "Quotation updated");
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("QUOTATION:EDIT")
    public ApiResponse<QuotationResponseDto> updateStatus(@PathVariable Long id,
                                                          @Valid @RequestBody QuotationStatusRequestDto req) {
        return ApiResponse.ok(service.updateStatus(id, req), "Status updated");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("QUOTATION:DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Quotation deleted");
    }
}