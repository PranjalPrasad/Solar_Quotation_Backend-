package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class GstRuleRequestDto {

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category is too long")
    private String category;

    @NotNull(message = "GST percent is required")
    @DecimalMin(value = "0", message = "GST cannot be negative")
    @DecimalMax(value = "100", message = "GST cannot exceed 100")
    private BigDecimal gstPercent;

    @NotBlank(message = "Applicable on is required")
    @Pattern(regexp = "PRODUCT|SERVICE", message = "Applicable on must be PRODUCT or SERVICE")
    private String applicableOn;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public String getApplicableOn() { return applicableOn; }
    public void setApplicableOn(String applicableOn) { this.applicableOn = applicableOn; }
}
