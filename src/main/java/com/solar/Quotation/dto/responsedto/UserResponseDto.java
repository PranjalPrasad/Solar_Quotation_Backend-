package com.solar.Quotation.dto.responsedto;

import java.time.LocalDateTime;

public class UserResponseDto {

    private Long id;
    private String code;
    private String name;
    private String email;
    private String phone;
    private String username;
    private String department;
    private String status;
    private RoleRefResponseDto role;
    private PartnerRefResponseDto partner;
    private String photoUrl;
    private LocalDateTime lastLoginAt;
    private boolean mustChangePassword;
    private AuditInfoResponseDto audit;
    /**
     * Filled ONLY when the server generated the password (create without a password, or reset without a
     * newPassword). Remove this field once e-mail sending is in place and send the password by e-mail instead.
     */
    private String temporaryPassword;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public RoleRefResponseDto getRole() { return role; }
    public void setRole(RoleRefResponseDto role) { this.role = role; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public AuditInfoResponseDto getAudit() { return audit; }
    public void setAudit(AuditInfoResponseDto audit) { this.audit = audit; }
    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }
}
