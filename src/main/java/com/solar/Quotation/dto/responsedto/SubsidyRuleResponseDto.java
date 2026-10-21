package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class SubsidyRuleResponseDto {

    private Long id;
    private String type;
    private String category;
    private String valueType;
    private BigDecimal value;
    private BigDecimal capMin;
    private BigDecimal capMax;
    private BigDecimal maxAmount;
    private boolean active;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
