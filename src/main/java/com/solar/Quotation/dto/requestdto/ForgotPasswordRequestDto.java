package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ForgotPasswordRequestDto {

    @NotBlank(message = "Email or username is required")
    @Size(max = 150, message = "Email or username is too long")
    private String identifier;

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
}
