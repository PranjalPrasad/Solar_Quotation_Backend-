package com.solar.Quotation.dto.responsedto;

import java.util.List;

/**
 * POST /api/auth/login response (inside ApiResponse.data).
 * Keeps the backend shape (accessToken, user, modules, permissions) AND adds the flat fields the login page
 * reads: token, role, userId, name, email, partnerId, partnerName.
 */
public class LoginResponseDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserInfoResponseDto user;
    private List<ModuleAccessResponseDto> modules;
    private List<String> permissions;

    public LoginResponseDto() { }

    public LoginResponseDto(String accessToken, String refreshToken, long expiresIn, CurrentUserResponseDto session) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = session.getUser();
        this.modules = session.getModules();
        this.permissions = session.getPermissions();
    }

    // ---- flat aliases for the frontend (session.js / login.html) ----
    public String getToken() { return accessToken; }
    public String getRole() { return user != null && user.getRole() != null ? user.getRole().getCode() : null; }
    public Long getUserId() { return user != null ? user.getId() : null; }
    public String getName() { return user != null ? user.getName() : null; }
    public String getEmail() { return user != null ? user.getEmail() : null; }
    public Long getPartnerId() { return user != null && user.getPartner() != null ? user.getPartner().getId() : null; }
    public String getPartnerName() { return user != null && user.getPartner() != null ? user.getPartner().getName() : null; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public UserInfoResponseDto getUser() { return user; }
    public void setUser(UserInfoResponseDto user) { this.user = user; }
    public List<ModuleAccessResponseDto> getModules() { return modules; }
    public void setModules(List<ModuleAccessResponseDto> modules) { this.modules = modules; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}