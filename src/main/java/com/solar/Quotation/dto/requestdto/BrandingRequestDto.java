package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** logoPath is the path returned by POST /api/files/upload (purpose LOGO). */
public class BrandingRequestDto {

    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name is too long")
    private String name;

    @Size(max = 300, message = "Address is too long")
    private String address;

    @Size(max = 30, message = "Phone is too long")
    private String phone;

    @Email(message = "Email is not valid")
    @Size(max = 150, message = "Email is too long")
    private String email;

    @Size(max = 150, message = "Website is too long")
    private String website;

    @Size(max = 300, message = "Logo path is too long")
    private String logoPath;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
}
