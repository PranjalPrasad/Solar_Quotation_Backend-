package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class CalculationDefaultsResponseDto {

    private BigDecimal gstPercent;
    private BigDecimal marketRatePerKw;

    public CalculationDefaultsResponseDto() { }

    public CalculationDefaultsResponseDto(BigDecimal gstPercent, BigDecimal marketRatePerKw) {
        this.gstPercent = gstPercent;
        this.marketRatePerKw = marketRatePerKw;
    }

    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal v) { this.gstPercent = v; }
    public BigDecimal getMarketRatePerKw() { return marketRatePerKw; }
    public void setMarketRatePerKw(BigDecimal v) { this.marketRatePerKw = v; }
}