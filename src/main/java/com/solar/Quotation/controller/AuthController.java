package com.solar.Quotation.controller;

import com.solar.Quotation.dto.requestdto.ChangePasswordRequestDto;
import com.solar.Quotation.dto.requestdto.ForgotPasswordRequestDto;
import com.solar.Quotation.dto.requestdto.LoginRequestDto;
import com.solar.Quotation.dto.requestdto.RefreshTokenRequestDto;
import com.solar.Quotation.dto.requestdto.ResetPasswordRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.CurrentUserResponseDto;
import com.solar.Quotation.dto.responsedto.LoginResponseDto;
import com.solar.Quotation.dto.responsedto.TokenResponseDto;
import com.solar.Quotation.service.AuthService;
import com.solar.Quotation.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * login, refresh, forgot-password and reset-password are public (see SecurityConfig and JwtAuthFilter).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService service, PasswordResetService passwordResetService) {
        this.service = service;
        this.passwordResetService = passwordResetService;
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

    /** Same answer whether or not the account exists, so the endpoint cannot be used to find valid users. */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto req) {
        passwordResetService.requestReset(req);
        return ApiResponse.ok(null,
                "If an account exists for that email or username, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto req) {
        passwordResetService.resetPassword(req);
        return ApiResponse.ok(null, "Password reset successful. Please log in with your new password.");
    }
}