package com.solar.Quotation.dto.requestdto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginRequestDto {

    @NotBlank(message = "Email or username is required")
    @JsonAlias({"email", "userName", "login"})
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    private boolean rememberMe;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}