package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class GstRuleResponseDto {

    private Long id;
    private String category;
    private BigDecimal gstPercent;
    private String applicableOn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public String getApplicableOn() { return applicableOn; }
    public void setApplicableOn(String applicableOn) { this.applicableOn = applicableOn; }
}
