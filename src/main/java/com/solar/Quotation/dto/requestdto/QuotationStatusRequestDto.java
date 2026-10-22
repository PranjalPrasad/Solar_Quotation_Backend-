package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class QuotationStatusRequestDto {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "PENDING|ACCEPTED|REJECTED", message = "Status must be PENDING, ACCEPTED or REJECTED")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
