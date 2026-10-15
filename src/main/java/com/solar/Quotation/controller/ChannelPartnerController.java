package com.solar.Quotation.controller;
import com.solar.Quotation.dto.requestdto.ChannelPartnerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerRequestDto;
import com.solar.Quotation.dto.requestdto.ChannelPartnerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.UserFilterRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.ChannelPartnerResponseDto;
import com.solar.Quotation.dto.responsedto.ChannelPartnerStatsResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerOptionResponseDto;
import com.solar.Quotation.dto.responsedto.UserResponseDto;
import com.solar.Quotation.security.RequirePermission;
import com.solar.Quotation.service.ChannelPartnerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Super Admin only (CHANNEL_PARTNER is a platform-only module).
 * Referral endpoints (/{id}/referrals and mark-paid) arrive with the referral module.
 */
@RestController
@RequestMapping("/api/channel-partners")
public class ChannelPartnerController {

    private final ChannelPartnerService service;

    public ChannelPartnerController(ChannelPartnerService service) {
        this.service = service;
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
}
