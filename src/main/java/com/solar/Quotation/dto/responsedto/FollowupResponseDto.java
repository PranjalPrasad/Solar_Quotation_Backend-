package com.solar.Quotation.dto.responsedto;

import java.time.LocalDate;

public class FollowupResponseDto {

    private Long id;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private LocalDate followupDate;
    private String remark;
    private LocalDate nextDate;
    /** PENDING, COMPLETED, or OVERDUE (a PENDING follow-up whose next date has passed). */
    private String status;
    private PartnerRefResponseDto partner;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public LocalDate getFollowupDate() { return followupDate; }
    public void setFollowupDate(LocalDate followupDate) { this.followupDate = followupDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
}
