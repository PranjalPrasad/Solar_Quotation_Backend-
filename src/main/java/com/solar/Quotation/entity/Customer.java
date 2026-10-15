package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.CustomerType;
import com.solar.Quotation.Enum.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "customer",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_partner_id", "mobile"}),
        indexes = {
                @Index(name = "idx_customer_partner_created", columnList = "channel_partner_id,created_at"),
                @Index(name = "idx_customer_partner_status", columnList = "channel_partner_id,status")
        })
public class Customer extends TenantBaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** 10 digits. Longer column because a deleted customer's mobile gets a suffix so it can be reused. */
    @Column(nullable = false, length = 40)
    private String mobile;

    @Column(length = 150)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(name = "gst_no", length = 20)
    private String gstNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType type = CustomerType.RESIDENTIAL;

    @Column(name = "consumer_no", length = 40)
    private String consumerNo;

    @Column(name = "consumer_category", length = 40)
    private String consumerCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
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
    public CustomerType getType() { return type; }
    public void setType(CustomerType type) { this.type = type; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public String getConsumerCategory() { return consumerCategory; }
    public void setConsumerCategory(String consumerCategory) { this.consumerCategory = consumerCategory; }
    public RecordStatus getStatus() { return status; }
    public void setStatus(RecordStatus status) { this.status = status; }
}

