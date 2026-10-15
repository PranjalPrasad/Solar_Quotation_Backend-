package com.solar.Quotation.dto.requestdto;

public class FollowupFilterRequestDto extends PageFilterRequestDto {

    private Long customerId;
    private String status;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
