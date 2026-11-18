package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Forgot-password flow. The size check here is only a first filter; the real rules (upper/lower/digit/symbol,
 * no username or e-mail name inside) live in PasswordPolicy.validate(...) and must be called by the service
 * that handles this request.
 */
public class ResetPasswordRequestDto {

    @NotBlank(message = "Reset token is required")
    @Size(max = 200, message = "Reset token is invalid")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 10, max = 100, message = "Password must be 10 to 100 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}