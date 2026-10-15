package com.solar.Quotation.dto.requestdto;

public class ProjectFilterRequestDto extends PageFilterRequestDto {

    private String type;
    private String status;
    private Long customerId;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
}
