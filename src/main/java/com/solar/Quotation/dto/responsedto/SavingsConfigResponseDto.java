package com.solar.Quotation.dto.responsedto;
import java.math.BigDecimal;

public class SavingsConfigResponseDto {

    private BigDecimal unitsPerKwPerDay;
    private BigDecimal degradationPercent;
    private BigDecimal co2FactorKgPerKwh;
    private int projectionYears;
    private BigDecimal marketRatePerKw;

    public BigDecimal getUnitsPerKwPerDay() { return unitsPerKwPerDay; }
    public void setUnitsPerKwPerDay(BigDecimal unitsPerKwPerDay) { this.unitsPerKwPerDay = unitsPerKwPerDay; }
    public BigDecimal getDegradationPercent() { return degradationPercent; }
    public void setDegradationPercent(BigDecimal degradationPercent) { this.degradationPercent = degradationPercent; }
    public BigDecimal getCo2FactorKgPerKwh() { return co2FactorKgPerKwh; }
    public void setCo2FactorKgPerKwh(BigDecimal co2FactorKgPerKwh) { this.co2FactorKgPerKwh = co2FactorKgPerKwh; }
    public int getProjectionYears() { return projectionYears; }
    public void setProjectionYears(int projectionYears) { this.projectionYears = projectionYears; }
    public BigDecimal getMarketRatePerKw() { return marketRatePerKw; }
    public void setMarketRatePerKw(BigDecimal marketRatePerKw) { this.marketRatePerKw = marketRatePerKw; }
}
