package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.ProjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "cost_calculation",
        indexes = {
                @Index(name = "idx_costcalc_partner_created", columnList = "channel_partner_id,created_at"),
                @Index(name = "idx_costcalc_partner_deleted", columnList = "channel_partner_id,deleted,created_by")
        })
public class CostCalculation extends TenantBaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "project_name", length = 150)
    private String projectName;

    @Column(name = "capacity_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacityKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 20)
    private ProjectType projectType;

    @Column(name = "material_cost", precision = 14, scale = 2)
    private BigDecimal materialCost;
    @Column(name = "installation_cost", precision = 14, scale = 2)
    private BigDecimal installationCost;
    @Column(name = "transportation_cost", precision = 14, scale = 2)
    private BigDecimal transportationCost;
    @Column(name = "civil_cost", precision = 14, scale = 2)
    private BigDecimal civilCost;
    @Column(name = "labour_cost", precision = 14, scale = 2)
    private BigDecimal labourCost;
    @Column(name = "electrical_cost", precision = 14, scale = 2)
    private BigDecimal electricalCost;
    @Column(name = "other_cost", precision = 14, scale = 2)
    private BigDecimal otherCost;
    @Column(precision = 14, scale = 2)
    private BigDecimal discount;

    @Column(name = "gst_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercent;

    @Column(precision = 14, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "gst_amount", precision = 14, scale = 2)
    private BigDecimal gstAmount;
    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;
    @Column(name = "cost_per_kw", precision = 14, scale = 2)
    private BigDecimal costPerKw;

    @Column(name = "estimated_saving", precision = 14, scale = 2)
    private BigDecimal estimatedSaving;
    @Column(name = "subsidy_amount", precision = 14, scale = 2)
    private BigDecimal subsidyAmount;
    @Column(name = "net_investment", precision = 14, scale = 2)
    private BigDecimal netInvestment;

    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { this.customerName = v; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { this.projectName = v; }
    public BigDecimal getCapacityKw() { return capacityKw; }
    public void setCapacityKw(BigDecimal v) { this.capacityKw = v; }
    public ProjectType getProjectType() { return projectType; }
    public void setProjectType(ProjectType v) { this.projectType = v; }
    public BigDecimal getMaterialCost() { return materialCost; }
    public void setMaterialCost(BigDecimal v) { this.materialCost = v; }
    public BigDecimal getInstallationCost() { return installationCost; }
    public void setInstallationCost(BigDecimal v) { this.installationCost = v; }
    public BigDecimal getTransportationCost() { return transportationCost; }
    public void setTransportationCost(BigDecimal v) { this.transportationCost = v; }
    public BigDecimal getCivilCost() { return civilCost; }
    public void setCivilCost(BigDecimal v) { this.civilCost = v; }
    public BigDecimal getLabourCost() { return labourCost; }
    public void setLabourCost(BigDecimal v) { this.labourCost = v; }
    public BigDecimal getElectricalCost() { return electricalCost; }
    public void setElectricalCost(BigDecimal v) { this.electricalCost = v; }
    public BigDecimal getOtherCost() { return otherCost; }
    public void setOtherCost(BigDecimal v) { this.otherCost = v; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal v) { this.discount = v; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal v) { this.gstPercent = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal v) { this.gstAmount = v; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal v) { this.grandTotal = v; }
    public BigDecimal getCostPerKw() { return costPerKw; }
    public void setCostPerKw(BigDecimal v) { this.costPerKw = v; }
    public BigDecimal getEstimatedSaving() { return estimatedSaving; }
    public void setEstimatedSaving(BigDecimal v) { this.estimatedSaving = v; }
    public BigDecimal getSubsidyAmount() { return subsidyAmount; }
    public void setSubsidyAmount(BigDecimal v) { this.subsidyAmount = v; }
    public BigDecimal getNetInvestment() { return netInvestment; }
    public void setNetInvestment(BigDecimal v) { this.netInvestment = v; }
}