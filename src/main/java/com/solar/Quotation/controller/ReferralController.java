package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.ReferralFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ReferralPaidRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerOptionResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralPartnerTotalsResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralSummaryResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.ReferralService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/referrals")
public class ReferralController {

    private final ReferralService service;

    public ReferralController(ReferralService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("REFERRAL:VIEW")
    public ApiResponse<PageResponse<ReferralResponseDto>> list(ReferralFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    /** Referrer picker of the quotation wizard: needs only QUOTATION:CREATE (id, code, name of other active partners). */
    @GetMapping("/referrer-options")
    @RequirePermission("QUOTATION:CREATE")
    public ApiResponse<List<PartnerOptionResponseDto>> referrerOptions() {
        return ApiResponse.ok(service.referrerOptions());
    }

    @GetMapping("/summary")
    @RequirePermission("REFERRAL:VIEW")
    public ApiResponse<ReferralSummaryResponseDto> summary(@RequestParam(required = false) Long partnerId,
                                                           @RequestParam(required = false) Long referrerPartnerId) {
        return ApiResponse.ok(service.summary(partnerId, referrerPartnerId));
    }

    /** Super Admin only (checked in the service). ?partnerIds=1,2,3 */
    @GetMapping("/partner-totals")
    @RequirePermission("REFERRAL:VIEW")
    public ApiResponse<List<ReferralPartnerTotalsResponseDto>> partnerTotals(@RequestParam List<Long> partnerIds) {
        return ApiResponse.ok(service.partnerTotals(partnerIds));
    }

    @GetMapping("/{id}")
    @RequirePermission("REFERRAL:VIEW")
    public ApiResponse<ReferralResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Super Admin only (checked in the service). */
    @GetMapping("/{id}/history")
    @RequirePermission("REFERRAL:VIEW")
    public ApiResponse<List<AuditLogResponseDto>> history(@PathVariable Long id) {
        return ApiResponse.ok(service.history(id));
    }

    /** Super Admin only (checked here by permission and again in the service). */
    @PatchMapping("/{id}/mark-paid")
    @RequirePermission("REFERRAL:MARK_PAID")
    public ApiResponse<ReferralResponseDto> markPaid(@PathVariable Long id,
                                                     @Valid @RequestBody ReferralPaidRequestDto req) {
        return ApiResponse.ok(service.markPaid(id, req), "Referral marked as paid");
    }
}
