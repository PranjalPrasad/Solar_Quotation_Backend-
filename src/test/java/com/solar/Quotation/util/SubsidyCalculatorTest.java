package com.solar.Quotation.util;

import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubsidyCalculatorTest {

    private static SubsidyRuleResponseDto rule(String category, String valueType, String value,
                                               String capMin, String capMax, String maxAmount, boolean active) {
        SubsidyRuleResponseDto r = new SubsidyRuleResponseDto();
        r.setType("test");
        r.setCategory(category);
        r.setValueType(valueType);
        r.setValue(new BigDecimal(value));
        r.setCapMin(new BigDecimal(capMin));
        r.setCapMax(capMax == null ? null : new BigDecimal(capMax));
        r.setMaxAmount(maxAmount == null ? null : new BigDecimal(maxAmount));
        r.setActive(active);
        return r;
    }

    private final List<SubsidyRuleResponseDto> seeded = List.of(
            rule("RESIDENTIAL", "PER_KW", "30000", "0", "2", null, true),
            rule("RESIDENTIAL", "PER_KW", "18000", "2", "3", null, true),
            rule("GHS", "PER_KW", "18000", "0", "500", null, true));

    private BigDecimal subsidy(String category, String kw) {
        return SubsidyCalculator.calculate(category, new BigDecimal(kw), new BigDecimal("300000"), seeded);
    }

    @Test
    void residentialSlabs() {
        assertEquals(new BigDecimal("45000.00"), subsidy("RESIDENTIAL", "1.5"));
        assertEquals(new BigDecimal("60000.00"), subsidy("RESIDENTIAL", "2"));
        assertEquals(new BigDecimal("69000.00"), subsidy("RESIDENTIAL", "2.5"));
        assertEquals(new BigDecimal("78000.00"), subsidy("RESIDENTIAL", "3"));
        assertEquals(new BigDecimal("78000.00"), subsidy("RESIDENTIAL", "5"));
        assertEquals(new BigDecimal("78000.00"), subsidy("RESIDENTIAL", "10"));
    }

    @Test
    void ghsIsPerKwUpTo500Kw() {
        assertEquals(new BigDecimal("1800000.00"), subsidy("GHS", "100"));
        assertEquals(new BigDecimal("9000000.00"), subsidy("GHS", "600"));
    }

    @Test
    void commercialHasNoSubsidy() {
        assertEquals(new BigDecimal("0.00"), subsidy("COMMERCIAL", "10"));
    }

    @Test
    void categoryIsMatchedIgnoringCase() {
        assertEquals(new BigDecimal("78000.00"), subsidy("residential", "5"));
    }

    @Test
    void inactiveRulesAreIgnored() {
        List<SubsidyRuleResponseDto> rules = List.of(rule("RESIDENTIAL", "PER_KW", "30000", "0", "2", null, false));
        assertEquals(new BigDecimal("0.00"),
                SubsidyCalculator.calculate("RESIDENTIAL", new BigDecimal("2"), new BigDecimal("1"), rules));
    }

    @Test
    void percentageRuleUsesProjectCostWhenSizeIsInRange() {
        List<SubsidyRuleResponseDto> rules = List.of(rule("RESIDENTIAL", "PERCENTAGE", "40", "0", "3", null, true));
        assertEquals(new BigDecimal("40000.00"),
                SubsidyCalculator.calculate("RESIDENTIAL", new BigDecimal("3"), new BigDecimal("100000"), rules));
        assertEquals(new BigDecimal("0.00"),
                SubsidyCalculator.calculate("RESIDENTIAL", new BigDecimal("4"), new BigDecimal("100000"), rules));
    }

    @Test
    void fixedAmountRuleAndPerRuleCap() {
        List<SubsidyRuleResponseDto> rules = List.of(
                rule("COMMERCIAL", "AMOUNT", "25000", "0", null, null, true),
                rule("COMMERCIAL", "PERCENTAGE", "50", "0", null, "10000", true));
        // 25,000 fixed + 50% of 100,000 capped at 10,000
        assertEquals(new BigDecimal("35000.00"),
                SubsidyCalculator.calculate("COMMERCIAL", new BigDecimal("8"), new BigDecimal("100000"), rules));
    }

    @Test
    void zeroSizeOrNoRulesGivesZero() {
        assertEquals(new BigDecimal("0.00"), SubsidyCalculator.calculate("RESIDENTIAL", BigDecimal.ZERO, BigDecimal.ONE, seeded));
        assertEquals(new BigDecimal("0.00"), SubsidyCalculator.calculate("RESIDENTIAL", new BigDecimal("3"), BigDecimal.ONE, null));
    }
}
