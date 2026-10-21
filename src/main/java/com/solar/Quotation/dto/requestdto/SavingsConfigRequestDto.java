package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** The constants behind the savings, ROI and CO2 figures of a quotation and the cost calculator. */
public class SavingsConfigRequestDto {

    @NotNull(message = "Units per kW per day is required")
    @DecimalMin(value = "0.1", message = "Must be at least 0.1")
    @DecimalMax(value = "24", message = "Cannot exceed 24")
    private BigDecimal unitsPerKwPerDay;

    @NotNull(message = "Degradation percent is required")
    @DecimalMin(value = "0", message = "Cannot be negative")
    @DecimalMax(value = "10", message = "Cannot exceed 10")
    private BigDecimal degradationPercent;

    @NotNull(message = "CO2 factor is required")
    @DecimalMin(value = "0", message = "Cannot be negative")
    @DecimalMax(value = "5", message = "Cannot exceed 5")
    private BigDecimal co2FactorKgPerKwh;

    @NotNull(message = "Projection years is required")
    @Min(value = 1, message = "At least 1 year")
    @Max(value = 40, message = "At most 40 years")
    private Integer projectionYears;

    @NotNull(message = "Market rate per kW is required")
    @DecimalMin(value = "0", message = "Cannot be negative")
    private BigDecimal marketRatePerKw;

    public BigDecimal getUnitsPerKwPerDay() { return unitsPerKwPerDay; }
    public void setUnitsPerKwPerDay(BigDecimal unitsPerKwPerDay) { this.unitsPerKwPerDay = unitsPerKwPerDay; }
    public BigDecimal getDegradationPercent() { return degradationPercent; }
    public void setDegradationPercent(BigDecimal degradationPercent) { this.degradationPercent = degradationPercent; }
    public BigDecimal getCo2FactorKgPerKwh() { return co2FactorKgPerKwh; }
    public void setCo2FactorKgPerKwh(BigDecimal co2FactorKgPerKwh) { this.co2FactorKgPerKwh = co2FactorKgPerKwh; }
    public Integer getProjectionYears() { return projectionYears; }
    public void setProjectionYears(Integer projectionYears) { this.projectionYears = projectionYears; }
    public BigDecimal getMarketRatePerKw() { return marketRatePerKw; }
    public void setMarketRatePerKw(BigDecimal marketRatePerKw) { this.marketRatePerKw = marketRatePerKw; }
}
