package com.solar.Quotation.entity;

import com.solar.Quotation.Enum.SubsidyValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One subsidy rule of a consumer category. The subsidy of a quotation is the sum of all matching active rules. */
@Entity
@Table(name = "subsidy_rule")
public class SubsidyRule extends BaseEntity {

    /** Display name, for example "PM Surya Ghar CFA: first 2 kW". */
    @Column(name = "subsidy_type", nullable = false, length = 150)
    private String type;

    /** RESIDENTIAL, GHS or COMMERCIAL (the consumer categories of the quotation form). */
    @Column(nullable = false, length = 20)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private SubsidyValueType valueType;

    @Column(name = "rule_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    /** Lower kW bound of the range (exclusive for PERCENTAGE and AMOUNT, slab start for PER_KW). */
    @Column(name = "cap_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal capMin = BigDecimal.ZERO;

    /** Upper kW bound (inclusive). Null means no upper limit. */
    @Column(name = "cap_max", precision = 10, scale = 2)
    private BigDecimal capMax;

    /** Optional ceiling for the amount this single rule can give. */
    @Column(name = "max_amount", precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public SubsidyValueType getValueType() { return valueType; }
    public void setValueType(SubsidyValueType valueType) { this.valueType = valueType; }
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
