package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * capMin and capMax are system sizes in kW. capMax empty means no upper limit.
 * valueType: PERCENTAGE (percent of project cost), AMOUNT (fixed), PER_KW (rupees per kW inside the range).
 */
public class SubsidyRuleRequestDto {

    @NotBlank(message = "Subsidy type is required")
    @Size(max = 150, message = "Subsidy type is too long")
    private String type;

    @NotBlank(message = "Category is required")
    @Pattern(regexp = "RESIDENTIAL|GHS|COMMERCIAL", message = "Category must be RESIDENTIAL, GHS or COMMERCIAL")
    private String category;

    @NotBlank(message = "Value type is required")
    @Pattern(regexp = "PERCENTAGE|AMOUNT|PER_KW", message = "Value type must be PERCENTAGE, AMOUNT or PER_KW")
    private String valueType;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0", message = "Value cannot be negative")
    private BigDecimal value;

    @DecimalMin(value = "0", message = "Minimum kW cannot be negative")
    private BigDecimal capMin;

    @DecimalMin(value = "0.01", message = "Maximum kW must be greater than 0")
    private BigDecimal capMax;

    @DecimalMin(value = "0", message = "Maximum amount cannot be negative")
    private BigDecimal maxAmount;

    private Boolean active;

    private Integer sortOrder;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getCapMin() { return capMin; }
    public void setCapMin(BigDecimal capMin) { this.capMin = capMin; }
    public BigDecimal getCapMax() { return capMax; }
    public void setCapMax(BigDecimal capMax) { this.capMax = capMax; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
