package com.solar.Quotation.dto.requestdto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create and update a channel partner. adminUser is required on create (the first CHANNEL_PARTNER_ADMIN
 * of that partner) and ignored on update.
 */
public class ChannelPartnerRequestDto {

    @NotBlank(message = "Partner name is required")
    @Size(max = 200, message = "Name is too long")
    private String name;

    @Size(max = 150, message = "Contact person is too long")
    private String contactPerson;

    @Size(max = 30, message = "Phone is too long")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    @Size(max = 150, message = "Email is too long")
    private String email;

    @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "GST number is not valid")
    private String gstNo;

    @Size(max = 500, message = "Address is too long")
    private String address;

    @DecimalMin(value = "0.00", message = "Referral percent cannot be negative")
    @DecimalMax(value = "100.00", message = "Referral percent cannot exceed 100")
    private BigDecimal referralPercent;

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
    private String status;

    @Valid
    private AdminUser adminUser;

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
    public BigDecimal getReferralPercent() { return referralPercent; }
    public void setReferralPercent(BigDecimal referralPercent) { this.referralPercent = referralPercent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public AdminUser getAdminUser() { return adminUser; }
    public void setAdminUser(AdminUser adminUser) { this.adminUser = adminUser; }

    /** First admin user of the new partner. */
    public static class AdminUser {

        @NotBlank(message = "Admin name is required")
        @Size(max = 150, message = "Admin name is too long")
        private String name;

        @NotBlank(message = "Admin email is required")
        @Email(message = "Admin email is not valid")
        @Size(max = 120, message = "Admin email is too long")
        private String email;

        @NotBlank(message = "Admin username is required")
        @Size(min = 3, max = 60, message = "Username must be 3 to 60 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain letters, digits, dot, underscore and hyphen")
        private String username;

        @Size(max = 30, message = "Phone is too long")
        private String phone;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }
}
