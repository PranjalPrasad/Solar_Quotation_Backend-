package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.CustomerType;
import com.solar.Quotation.Enum.ProjectStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "project",
        indexes = {
                @Index(name = "idx_project_partner_created", columnList = "channel_partner_id,created_at"),
                @Index(name = "idx_project_partner_status", columnList = "channel_partner_id,status"),
                @Index(name = "idx_project_customer", columnList = "customer_id")
        })
public class Project extends TenantBaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 20)
    private CustomerType type = CustomerType.RESIDENTIAL;

    @Column(name = "capacity_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacityKw;

    @Column(name = "roof_type", length = 50)
    private String roofType;

    /** Units (kWh) per month. */
    @Column(name = "monthly_consumption", precision = 12, scale = 2)
    private BigDecimal monthlyConsumption;

    @Column(length = 200)
    private String location;

    /** Square feet. */
    @Column(name = "roof_area", precision = 12, scale = 2)
    private BigDecimal roofArea;

    /** Units (kWh) per month. */
    @Column(name = "estimated_generation", precision = 12, scale = 2)
    private BigDecimal estimatedGeneration;

    @Column(name = "installation_type", length = 30)
    private String installationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.PLANNING;

    @Column(length = 500)
    private String remarks;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public CustomerType getType() { return type; }
    public void setType(CustomerType type) { this.type = type; }
    public BigDecimal getCapacityKw() { return capacityKw; }
    public void setCapacityKw(BigDecimal capacityKw) { this.capacityKw = capacityKw; }
    public String getRoofType() { return roofType; }
    public void setRoofType(String roofType) { this.roofType = roofType; }
    public BigDecimal getMonthlyConsumption() { return monthlyConsumption; }
    public void setMonthlyConsumption(BigDecimal monthlyConsumption) { this.monthlyConsumption = monthlyConsumption; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getRoofArea() { return roofArea; }
    public void setRoofArea(BigDecimal roofArea) { this.roofArea = roofArea; }
    public BigDecimal getEstimatedGeneration() { return estimatedGeneration; }
    public void setEstimatedGeneration(BigDecimal estimatedGeneration) { this.estimatedGeneration = estimatedGeneration; }
    public String getInstallationType() { return installationType; }
    public void setInstallationType(String installationType) { this.installationType = installationType; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
