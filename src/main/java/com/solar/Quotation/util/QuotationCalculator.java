package com.solar.Quotation.util;


import com.solar.Quotation.Enum.DiscountType;
import com.solar.Quotation.dto.responsedto.QuotationResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * All quotation money maths in ONE place (replaces computeTotals / computeSubsidy / attachSubsidyAndSavings
 * in quotation.js). Pure functions, no Spring, so it is easy to unit test.
 */
public final class QuotationCalculator {

    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private QuotationCalculator() { }

    public static final class Totals {
        public final BigDecimal subtotal;
        public final BigDecimal discountAmount;
        public final BigDecimal taxable;
        public final BigDecimal sgst;
        public final BigDecimal cgst;
        public final BigDecimal total;

        Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxable,
               BigDecimal sgst, BigDecimal cgst, BigDecimal total) {
            this.subtotal = subtotal;
            this.discountAmount = discountAmount;
            this.taxable = taxable;
            this.sgst = sgst;
            this.cgst = cgst;
            this.total = total;
        }
    }

    /**
     * subtotal = items + installation + transport + other
     * discount = percent of subtotal, or a flat amount (never more than the subtotal)
     * taxable  = subtotal - discount
     * SGST and CGST are each half of the GST on the taxable amount; total = taxable + SGST + CGST
     */
    public static Totals totals(BigDecimal itemsTotal, BigDecimal installation, BigDecimal transport,
                                BigDecimal other, DiscountType discountType, BigDecimal discountValue,
                                BigDecimal gstPercent) {
        BigDecimal subtotal = MoneyUtil.scale(MoneyUtil.nz(itemsTotal).add(MoneyUtil.nz(installation))
                .add(MoneyUtil.nz(transport)).add(MoneyUtil.nz(other)));
        BigDecimal discount = discountType == DiscountType.PERCENT
                ? MoneyUtil.percentOf(subtotal, discountValue)
                : MoneyUtil.scale(discountValue);
        discount = MoneyUtil.min(discount, subtotal);
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal gst = MoneyUtil.percentOf(taxable, gstPercent);
        BigDecimal sgst = gst.divide(TWO, 2, RoundingMode.HALF_UP);
        BigDecimal cgst = gst.subtract(sgst);
        BigDecimal total = MoneyUtil.scale(taxable.add(sgst).add(cgst));
        return new Totals(subtotal, discount, taxable, sgst, cgst, total);
    }

    /**
     * Year-by-year savings: units fall by the degradation % every year, the tariff rises by the escalation %.
     * netInvestment = total - subsidy (floored at 0).
     */
    public static QuotationResponseDto.Savings savings(BigDecimal sizeKw, BigDecimal tariff, BigDecimal escalationPercent,
                                                       BigDecimal netInvestment, SavingsConfigResponseDto cfg) {
        BigDecimal annualUnits = MoneyUtil.nz(sizeKw).multiply(cfg.getUnitsPerKwPerDay()).multiply(DAYS_PER_YEAR);
        BigDecimal annualSavings = annualUnits.multiply(MoneyUtil.nz(tariff));

        BigDecimal degradeFactor = BigDecimal.ONE.subtract(cfg.getDegradationPercent().divide(MoneyUtil.HUNDRED, MC));
        BigDecimal escalateFactor = BigDecimal.ONE.add(MoneyUtil.nz(escalationPercent).divide(MoneyUtil.HUNDRED, MC));
        BigDecimal units = annualUnits;
        BigDecimal rate = MoneyUtil.nz(tariff);
        BigDecimal lifetime = BigDecimal.ZERO;
        for (int y = 1; y <= cfg.getProjectionYears(); y++) {
            lifetime = lifetime.add(units.multiply(rate, MC), MC);
            units = units.multiply(degradeFactor, MC);
            rate = rate.multiply(escalateFactor, MC);
        }

        BigDecimal carbonPerYear = annualUnits.multiply(cfg.getCo2FactorKgPerKwh());
        BigDecimal carbonLifetimeTonnes = carbonPerYear.multiply(BigDecimal.valueOf(cfg.getProjectionYears()))
                .divide(THOUSAND, MC);

        QuotationResponseDto.Savings s = new QuotationResponseDto.Savings();
        s.setProjectionYears(cfg.getProjectionYears());
        s.setAnnualUnits(MoneyUtil.scale(annualUnits));
        s.setAnnualSavings(MoneyUtil.scale(annualSavings));
        s.setMonthlySavings(MoneyUtil.scale(annualSavings.divide(new BigDecimal("12"), MC)));
        s.setSavings25yr(MoneyUtil.scale(lifetime));
        s.setCarbonPerYearKg(MoneyUtil.scale(carbonPerYear));
        s.setCarbon25yrTonnes(MoneyUtil.scale(carbonLifetimeTonnes));
        boolean canDivide = netInvestment != null && netInvestment.signum() > 0 && annualSavings.signum() > 0;
        s.setRoiPercent(canDivide
                ? MoneyUtil.scale(annualSavings.multiply(MoneyUtil.HUNDRED).divide(netInvestment, MC))
                : MoneyUtil.scale(BigDecimal.ZERO));
        s.setPaybackYears(canDivide
                ? MoneyUtil.scale(netInvestment.divide(annualSavings, MC))
                : MoneyUtil.scale(BigDecimal.ZERO));
        return s;
    }
}
