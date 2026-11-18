package com.solar.Quotation.dto.requestdto;

import com.solar.Quotation.Enum.ProjectType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The numbers of a calculation, without the customer. Used by POST /api/calculations/preview (so the screen can ask
 * for the settings-based figures before a customer is typed) and extended by CalculationRequestDto for save.
 * gstPercent is optional: when it is missing the caller's own tenant default is used (see CalculatorServiceImpl).
 */
public class CalculationPreviewRequestDto {

    /** 7 heads x 2 (100% GST) must still fit DECIMAL(15,2), so a single head is capped at 10,000 crore. */
    public static final String MAX_MONEY = "100000000000";

    @NotNull @DecimalMin("0.01")
    private BigDecimal capacityKw;

    @NotNull
    private ProjectType projectType;

    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal materialCost;
    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal installationCost;
    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal transportationCost;
    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal civilCost;
    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal labourCost;
    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal electricalCost;
    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal otherCost;
    @DecimalMin("0") @DecimalMax(MAX_MONEY) private BigDecimal discount;

    @DecimalMin("0") @DecimalMax("100")
    private BigDecimal gstPercent;

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
}