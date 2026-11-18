package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.CalculationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CalculationPreviewRequestDto;
import com.solar.Quotation.dto.requestdto.CalculationRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.CalculationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.CalculationResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.CalculatorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculations")
public class CalculatorController {

    private final CalculatorService service;

    public CalculatorController(CalculatorService service) {
        this.service = service;
    }

    /** Starting values (default GST %, market rate) from the caller's own tenant settings. */
    @GetMapping("/defaults")
    @RequirePermission("CALCULATOR:VIEW")
    public ApiResponse<CalculationDefaultsResponseDto> defaults() {
        return ApiResponse.ok(service.defaults());
    }

    /** Calculates with the caller's own tenant settings. Saves and audits nothing. */
    @PostMapping("/preview")
    @RequirePermission("CALCULATOR:VIEW")
    public ApiResponse<CalculationResponseDto> preview(@Valid @RequestBody CalculationPreviewRequestDto req) {
        return ApiResponse.ok(service.preview(req));
    }

    @GetMapping
    @RequirePermission("CALCULATOR:VIEW")
    public ApiResponse<PageResponse<CalculationResponseDto>> list(CalculationFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    @GetMapping("/{id}")
    @RequirePermission("CALCULATOR:VIEW")
    public ApiResponse<CalculationResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    @RequirePermission("CALCULATOR:CREATE")
    public ResponseEntity<ApiResponse<CalculationResponseDto>> create(@Valid @RequestBody CalculationRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req), "Calculation created"));
    }

    @PutMapping("/{id}")
    @RequirePermission("CALCULATOR:EDIT")
    public ApiResponse<CalculationResponseDto> update(@PathVariable Long id, @Valid @RequestBody CalculationRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "Calculation updated");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("CALCULATOR:DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Calculation deleted");
    }
}