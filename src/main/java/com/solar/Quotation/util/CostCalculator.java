package com.solar.Quotation.util;

import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;

import java.math.BigDecimal;
import java.util.List;

public final class CostCalculator {

    private CostCalculator() { }

    public static final class Input {
        public BigDecimal capacityKw;
        public String projectType;
        public BigDecimal material, installation, transportation, civil, labour, electrical, other;
        public BigDecimal discount;
        public BigDecimal gstPercent;
    }

    public static final class Result {
        public final BigDecimal gross;
        public final BigDecimal subtotal;
        public final BigDecimal gstAmount;
        public final BigDecimal grandTotal;
        public final BigDecimal costPerKw;
        public final BigDecimal estimatedSaving;
        public final BigDecimal subsidyAmount;
        public final BigDecimal netInvestment;

        Result(BigDecimal gross, BigDecimal subtotal, BigDecimal gstAmount, BigDecimal grandTotal, BigDecimal costPerKw,
               BigDecimal estimatedSaving, BigDecimal subsidyAmount, BigDecimal netInvestment) {
            this.gross = gross;
            this.subtotal = subtotal;
            this.gstAmount = gstAmount;
            this.grandTotal = grandTotal;
            this.costPerKw = costPerKw;
            this.estimatedSaving = estimatedSaving;
            this.subsidyAmount = subsidyAmount;
            this.netInvestment = netInvestment;
        }
    }

    public static BigDecimal gross(Input in) {
        return MoneyUtil.scale(MoneyUtil.nz(in.material).add(MoneyUtil.nz(in.installation))
                .add(MoneyUtil.nz(in.transportation)).add(MoneyUtil.nz(in.civil))
                .add(MoneyUtil.nz(in.labour)).add(MoneyUtil.nz(in.electrical)).add(MoneyUtil.nz(in.other)));
    }

    public static Result compute(Input in, SavingsConfigResponseDto savings, List<SubsidyRuleResponseDto> subsidyRules) {
        BigDecimal capacity = MoneyUtil.nz(in.capacityKw);
        BigDecimal gross = gross(in);
        BigDecimal subtotal = gross.subtract(MoneyUtil.scale(in.discount));
        BigDecimal gst = MoneyUtil.percentOf(subtotal, in.gstPercent);
        BigDecimal grand = MoneyUtil.scale(subtotal.add(gst));

        BigDecimal perKw = capacity.signum() > 0
                ? grand.divide(capacity, 2, java.math.RoundingMode.HALF_UP)
                : MoneyUtil.scale(BigDecimal.ZERO);

        BigDecimal marketRate = savings == null ? null : savings.getMarketRatePerKw();
        BigDecimal saving = marketRate == null
                ? MoneyUtil.scale(BigDecimal.ZERO)
                : MoneyUtil.scale(MoneyUtil.max(BigDecimal.ZERO, marketRate.multiply(capacity).subtract(grand)));

        BigDecimal subsidy = SubsidyCalculator.calculate(in.projectType, capacity, grand, subsidyRules);
        BigDecimal net = MoneyUtil.scale(MoneyUtil.max(BigDecimal.ZERO, grand.subtract(subsidy)));

        return new Result(gross, subtotal, gst, grand, perKw, saving, subsidy, net);
    }
}