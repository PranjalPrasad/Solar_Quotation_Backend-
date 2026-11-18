package com.solar.Quotation.controller;

import com.solar.Quotation.dto.responsedto.AccessResponseDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.service.AccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Any signed-in user: returns only what that user's own role allows. No @RequirePermission on purpose. */
@RestController
@RequestMapping("/api/me")
public class AccessController {

    private final AccessService service;

    public AccessController(AccessService service) {
        this.service = service;
    }

    @GetMapping("/access")
    public ApiResponse<AccessResponseDto> access() {
        return ApiResponse.ok(service.currentAccess());
    }
}