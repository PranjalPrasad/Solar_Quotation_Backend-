package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.GstApplicableOn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "gst_rule")
public class GstRule extends TenantBaseEntity {

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "gst_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_on", nullable = false, length = 20)
    private GstApplicableOn applicableOn = GstApplicableOn.PRODUCT;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public GstApplicableOn getApplicableOn() { return applicableOn; }
    public void setApplicableOn(GstApplicableOn applicableOn) { this.applicableOn = applicableOn; }
}