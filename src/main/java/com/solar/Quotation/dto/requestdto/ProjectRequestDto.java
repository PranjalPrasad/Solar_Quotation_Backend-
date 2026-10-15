package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create and update. The project belongs to the partner of its customer, so there is no partnerId here:
 * Super Admin just picks a customer.
 */
public class ProjectRequestDto {

    @NotBlank(message = "Project name is required")
    @Size(max = 200, message = "Name is too long")
    private String name;

    @NotNull(message = "Customer is required")
    private Long customerId;

    @NotBlank(message = "Project type is required")
    @Pattern(regexp = "RESIDENTIAL|COMMERCIAL|INDUSTRIAL", message = "Type must be RESIDENTIAL, COMMERCIAL or INDUSTRIAL")
    private String type;

    @NotNull(message = "Capacity is required")
    @DecimalMin(value = "0.1", message = "Capacity must be at least 0.1 kW")
    @DecimalMax(value = "100000", message = "Capacity is too large")
    private BigDecimal capacityKw;

    @Size(max = 50, message = "Roof type is too long")
    private String roofType;

    @DecimalMin(value = "0", message = "Monthly consumption cannot be negative")
    private BigDecimal monthlyConsumption;

    @Size(max = 200, message = "Location is too long")
    private String location;

    @DecimalMin(value = "0", message = "Roof area cannot be negative")
    private BigDecimal roofArea;

    @DecimalMin(value = "0", message = "Estimated generation cannot be negative")
    private BigDecimal estimatedGeneration;

    @Size(max = 30, message = "Installation type is too long")
    private String installationType;

    @Pattern(regexp = "PLANNING|IN_PROGRESS|COMPLETED|ON_HOLD", message = "Unknown project status")
    private String status;

    @Size(max = 500, message = "Remarks are too long")
    private String remarks;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
