package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create and update. partnerId is read only for SUPER_ADMIN on create (required there); ignored for everybody else. */
public class CustomerRequestDto {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name is too long")
    private String name;

    @NotBlank(message = "Mobile is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile must be 10 digits")
    private String mobile;

    @Email(message = "Email is not valid")
    @Size(max = 150, message = "Email is too long")
    private String email;

    @Size(max = 300, message = "Address is too long")
    private String address;

    @Size(max = 80, message = "City is too long")
    private String city;

    @Size(max = 80, message = "State is too long")
    private String state;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be 6 digits")
    private String pincode;

    @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "GST number is not valid")
    private String gstNo;

    @NotBlank(message = "Customer type is required")
    @Pattern(regexp = "RESIDENTIAL|COMMERCIAL|INDUSTRIAL", message = "Type must be RESIDENTIAL, COMMERCIAL or INDUSTRIAL")
    private String type;

    @Size(max = 40, message = "Consumer number is too long")
    private String consumerNo;

    @Size(max = 40, message = "Consumer category is too long")
    private String consumerCategory;

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
    private String status;

    private Long partnerId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getGstNo() { return gstNo; }
    public void setGstNo(String gstNo) { this.gstNo = gstNo; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public String getConsumerCategory() { return consumerCategory; }
    public void setConsumerCategory(String consumerCategory) { this.consumerCategory = consumerCategory; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
}
