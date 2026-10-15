package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ChannelPartnerResponseDto {

    private Long id;
    private String code;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String gstNo;
    private String address;
    private String status;
    private String referralCode;
    private BigDecimal referralPercent;
    private LocalDateTime createdAt;
    private ChannelPartnerStatsResponseDto stats;
    /** Only on create: the first admin user that was created together with the partner. */
    private AdminUserInfo adminUser;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGstNo() { return gstNo; }
    public void setGstNo(String gstNo) { this.gstNo = gstNo; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public BigDecimal getReferralPercent() { return referralPercent; }
    public void setReferralPercent(BigDecimal referralPercent) { this.referralPercent = referralPercent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public ChannelPartnerStatsResponseDto getStats() { return stats; }
    public void setStats(ChannelPartnerStatsResponseDto stats) { this.stats = stats; }
    public AdminUserInfo getAdminUser() { return adminUser; }
    public void setAdminUser(AdminUserInfo adminUser) { this.adminUser = adminUser; }

    public static class AdminUserInfo {
        private Long id;
        private String code;
        private String username;
        /** Filled only because no e-mail sender exists yet. Remove together with UserResponseDto.temporaryPassword. */
        private String temporaryPassword;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getTemporaryPassword() { return temporaryPassword; }
        public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }
    }
}
