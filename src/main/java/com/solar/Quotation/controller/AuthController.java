package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.ChangePasswordRequestDto;
import com.solar.Quotation.dto.requestdto.LoginRequestDto;
import com.solar.Quotation.dto.requestdto.RefreshTokenRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.CurrentUserResponseDto;
import com.solar.Quotation.dto.responsedto.LoginResponseDto;
import com.solar.Quotation.dto.responsedto.TokenResponseDto;
import com.solar.Quotation.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * login and refresh are public (see SecurityConfig). forgot-password and reset-password need an e-mail sender
 * and are added with the mail setup.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto req) {
        return ApiResponse.ok(service.login(req), "Login successful");
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto req) {
        return ApiResponse.ok(service.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequestDto req) {
        service.logout(req);
        return ApiResponse.ok(null, "Logged out");
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponseDto> me() {
        return ApiResponse.ok(service.me());
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDto req) {
        service.changePassword(req);
        return ApiResponse.ok(null, "Password changed. Please log in again.");
    }
}
