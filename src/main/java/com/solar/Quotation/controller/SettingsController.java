package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.BankDetailsRequestDto;
import com.solar.Quotation.dto.requestdto.BrandingRequestDto;
import com.solar.Quotation.dto.requestdto.GstRuleRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationDefaultsRequestDto;
import com.solar.Quotation.dto.requestdto.SavingsConfigRequestDto;
import com.solar.Quotation.dto.requestdto.SubsidyRuleRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.PublicSettingsResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.SettingsService;
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

import java.util.List;

/** SETTINGS is a platform-only module, so everything here except /public is Super Admin only. */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    /**
     * Read-only settings for EVERY logged-in role (branding, bank, defaults, savings constants, active subsidy
     * and GST rules). No @RequirePermission on purpose: it holds nothing secret and the quotation screen needs it.
     */
    @GetMapping("/public")
    public ApiResponse<PublicSettingsResponseDto> publicSettings() {
        return ApiResponse.ok(service.publicSettings());
    }

    // ---- branding

    @GetMapping("/branding")
    @RequirePermission("SETTINGS:VIEW")
    public ApiResponse<BrandingResponseDto> getBranding() {
        return ApiResponse.ok(service.getBranding());
    }

    @PutMapping("/branding")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<BrandingResponseDto> updateBranding(@Valid @RequestBody BrandingRequestDto req) {
        return ApiResponse.ok(service.updateBranding(req), "Branding saved");
    }

    // ---- bank details

    @GetMapping("/bank-details")
    @RequirePermission("SETTINGS:VIEW")
    public ApiResponse<BankDetailsResponseDto> getBank() {
        return ApiResponse.ok(service.getBank());
    }

    @PutMapping("/bank-details")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<BankDetailsResponseDto> updateBank(@Valid @RequestBody BankDetailsRequestDto req) {
        return ApiResponse.ok(service.updateBank(req), "Bank details saved");
    }

    // ---- quotation defaults

    @GetMapping("/quotation-defaults")
    @RequirePermission("SETTINGS:VIEW")
    public ApiResponse<QuotationDefaultsResponseDto> getQuotationDefaults() {
        return ApiResponse.ok(service.getQuotationDefaults());
    }

    @PutMapping("/quotation-defaults")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<QuotationDefaultsResponseDto> updateQuotationDefaults(
            @Valid @RequestBody QuotationDefaultsRequestDto req) {
        return ApiResponse.ok(service.updateQuotationDefaults(req), "Quotation defaults saved");
    }

    // ---- savings config

    @GetMapping("/savings-config")
    @RequirePermission("SETTINGS:VIEW")
    public ApiResponse<SavingsConfigResponseDto> getSavingsConfig() {
        return ApiResponse.ok(service.getSavingsConfig());
    }

    @PutMapping("/savings-config")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<SavingsConfigResponseDto> updateSavingsConfig(@Valid @RequestBody SavingsConfigRequestDto req) {
        return ApiResponse.ok(service.updateSavingsConfig(req), "Savings configuration saved");
    }

    // ---- subsidy rules

    @GetMapping("/subsidy-rules")
    @RequirePermission("SETTINGS:VIEW")
    public ApiResponse<List<SubsidyRuleResponseDto>> subsidyRules() {
        return ApiResponse.ok(service.subsidyRules());
    }

    @PostMapping("/subsidy-rules")
    @RequirePermission("SETTINGS:EDIT")
    public ResponseEntity<ApiResponse<SubsidyRuleResponseDto>> createSubsidyRule(
            @Valid @RequestBody SubsidyRuleRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createSubsidyRule(req), "Subsidy rule created"));
    }

    @PutMapping("/subsidy-rules/{id}")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<SubsidyRuleResponseDto> updateSubsidyRule(@PathVariable Long id,
                                                                 @Valid @RequestBody SubsidyRuleRequestDto req) {
        return ApiResponse.ok(service.updateSubsidyRule(id, req), "Subsidy rule updated");
    }

    @DeleteMapping("/subsidy-rules/{id}")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<Void> deleteSubsidyRule(@PathVariable Long id) {
        service.deleteSubsidyRule(id);
        return ApiResponse.ok(null, "Subsidy rule deleted");
    }

    // ---- GST rules

    @GetMapping("/gst-rules")
    @RequirePermission("SETTINGS:VIEW")
    public ApiResponse<List<GstRuleResponseDto>> gstRules() {
        return ApiResponse.ok(service.gstRules());
    }

    @PostMapping("/gst-rules")
    @RequirePermission("SETTINGS:EDIT")
    public ResponseEntity<ApiResponse<GstRuleResponseDto>> createGstRule(@Valid @RequestBody GstRuleRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createGstRule(req), "GST rule created"));
    }

    @PutMapping("/gst-rules/{id}")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<GstRuleResponseDto> updateGstRule(@PathVariable Long id,
                                                         @Valid @RequestBody GstRuleRequestDto req) {
        return ApiResponse.ok(service.updateGstRule(id, req), "GST rule updated");
    }

    @DeleteMapping("/gst-rules/{id}")
    @RequirePermission("SETTINGS:EDIT")
    public ApiResponse<Void> deleteGstRule(@PathVariable Long id) {
        service.deleteGstRule(id);
        return ApiResponse.ok(null, "GST rule deleted");
    }
}
