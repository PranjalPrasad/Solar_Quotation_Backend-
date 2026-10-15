package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.Size;

/** newPassword is optional. When blank the server generates a temporary password. */
public class AdminResetPasswordRequestDto {

    @Size(max = 100, message = "Password is too long")
    private String newPassword;

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
