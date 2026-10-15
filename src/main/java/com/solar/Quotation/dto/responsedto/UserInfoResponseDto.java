package com.solar.Quotation.dto.responsedto;

public class UserInfoResponseDto {

    private Long id;
    private String name;
    private String email;
    private String username;
    private String photoUrl;
    private boolean mustChangePassword;
    private RoleRefResponseDto role;
    private PartnerRefResponseDto partner;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public RoleRefResponseDto getRole() { return role; }
    public void setRole(RoleRefResponseDto role) { this.role = role; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
}