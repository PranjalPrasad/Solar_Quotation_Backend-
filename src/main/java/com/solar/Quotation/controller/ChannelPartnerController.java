package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.*;
import com.solar.Quotation.dto.responsedto.*;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.ChannelPartnerService;
import com.solar.Quotation.service.PartnerAllowedRoleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Super Admin only (PlatformOnlyInterceptor bhi enforce karta hai).
 * /api/partners = spec wala alias, /api/channel-partners = purana path.
 */
@RestController
@RequestMapping({"/api/channel-partners", "/api/partners"})
public class ChannelPartnerController {

    private final ChannelPartnerService service;
    private final PartnerAllowedRoleService allowedRoles;

    public ChannelPartnerController(ChannelPartnerService service, PartnerAllowedRoleService allowedRoles) {
        this.service = service;
        this.allowedRoles = allowedRoles;
    }

    @GetMapping
    @RequirePermission("CHANNEL_PARTNER:VIEW")
    public ApiResponse<PageResponse<ChannelPartnerResponseDto>> list(ChannelPartnerFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    @GetMapping("/options")
    @RequirePermission("CHANNEL_PARTNER:VIEW")
    public ApiResponse<List<PartnerOptionResponseDto>> options() {
        return ApiResponse.ok(service.options());
    }

    @PostMapping
    @RequirePermission("CHANNEL_PARTNER:CREATE")
    public ResponseEntity<ApiResponse<ChannelPartnerResponseDto>> create(@Valid @RequestBody ChannelPartnerRequestDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Channel partner created"));
    }

    @GetMapping("/{id}")
    @RequirePermission("CHANNEL_PARTNER:VIEW")
    public ApiResponse<ChannelPartnerResponseDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @RequirePermission("CHANNEL_PARTNER:EDIT")
    public ApiResponse<ChannelPartnerResponseDto> update(@PathVariable Long id,
                                                         @Valid @RequestBody ChannelPartnerRequestDto req) {
        return ApiResponse.ok(service.update(id, req), "Channel partner updated");
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("CHANNEL_PARTNER:EDIT")
    public ApiResponse<ChannelPartnerResponseDto> updateStatus(@PathVariable Long id,
                                                               @Valid @RequestBody ChannelPartnerStatusRequestDto req) {
        return ApiResponse.ok(service.updateStatus(id, req), "Status updated");
    }

    @GetMapping("/{id}/stats")
    @RequirePermission("CHANNEL_PARTNER:VIEW")
    public ApiResponse<ChannelPartnerStatsResponseDto> stats(@PathVariable Long id) {
        return ApiResponse.ok(service.stats(id));
    }

    @GetMapping("/{id}/users")
    @RequirePermission("CHANNEL_PARTNER:VIEW")
    public ApiResponse<PageResponse<UserResponseDto>> users(@PathVariable Long id, UserFilterRequestDto filter) {
        return ApiResponse.ok(service.users(id, filter));
    }

    @GetMapping("/{id}/allowed-roles")
    @RequirePermission("CHANNEL_PARTNER:VIEW")
    public ApiResponse<AllowedRolesResponseDto> allowedRoles(@PathVariable Long id) {
        return ApiResponse.ok(allowedRoles.get(id));
    }

    @PutMapping("/{id}/allowed-roles")
    @RequirePermission("CHANNEL_PARTNER:EDIT")
    public ApiResponse<AllowedRolesResponseDto> updateAllowedRoles(@PathVariable Long id,
                                                                   @Valid @RequestBody AllowedRolesRequestDto req) {
        return ApiResponse.ok(allowedRoles.replace(id, req), "Allowed roles updated");
    }

    @GetMapping("/{id}/referrals")
    @RequirePermission("CHANNEL_PARTNER:VIEW")
    public ResponseEntity<ApiResponse<PageResponse<ReferralActivityResponseDto>>> referrals(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getReferralActivity(
                id, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @PatchMapping("/{id}/referrals/{quotationId}/mark-paid")
    @RequirePermission("CHANNEL_PARTNER:MARK_PAID")
    public ResponseEntity<ApiResponse<Void>> markPaid(
            @PathVariable Long id,
            @PathVariable Long quotationId,
            @Valid @RequestBody ReferralMarkPaidRequestDto req) {
        service.markReferralPaid(id, quotationId, req);
        return ResponseEntity.ok(ApiResponse.ok(null, "Referral marked paid"));
    }
}