package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create and update. The owner is ALWAYS the caller's own tenant; the legacy partnerId below is never used to
 * choose it (TenantResolver logs a WARN when it differs from the caller's own partner).
 */
public class CalculationRequestDto extends CalculationPreviewRequestDto {

    private Long partnerId;

    @NotBlank @Size(max = 150)
    private String customerName;

    @Size(max = 150)
    private String projectName;

    @Deprecated public Long getPartnerId() { return partnerId; }
    @Deprecated public void setPartnerId(Long v) { this.partnerId = v; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { this.customerName = v; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { this.projectName = v; }
}