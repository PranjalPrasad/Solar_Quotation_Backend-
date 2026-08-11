package com.solar.Quotation.dto.responsedto;

public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String role;
    private Long partnerId;
    private String partnerName;
    private String name;

    public LoginResponse() {
    }

    public LoginResponse(String token, Long userId, String username, String role,
                         Long partnerId, String partnerName, String name) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.partnerId = partnerId;
        this.partnerName = partnerName;
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

